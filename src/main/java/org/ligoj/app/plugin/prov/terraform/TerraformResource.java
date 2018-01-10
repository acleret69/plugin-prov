package org.ligoj.app.plugin.prov.terraform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.QuoteVo;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.PluginsClassLoader;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Terraforming resource
 */
@Slf4j
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class TerraformResource {

	/**
	 * Main log file.
	 */
	private static final String MAIN_LOG = "main.log";

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected ProvResource resource;

	@Autowired
	protected TerraformRunnerResource runner;

	@Autowired
	protected ServicePluginLocator locator;

	@Autowired
	protected TerraformUtils terraformUtils;

	/**
	 * Produce the Terraform configuration.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @param file
	 *            The target file name.
	 * @return the {@link Response} ready to be consumed.
	 * @throws IOException
	 *             When Terraform content cannot be written.
	 */
	@GET
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Path("{subscription:\\d+}/{file:.*.tf}")
	public Response getTerraform(@PathParam("subscription") final int subscription, @PathParam("file") final String file)
			throws IOException {
		final Terraforming terra = getTerraform(subscription);
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		terra.terraform(output, subscription, resource.getConfiguration(subscription));
		return AbstractToolPluginResource.download(o -> o.write(output.toByteArray()), file).build();
	}

	/**
	 * Get the log of the current or last Terraform execution of a given
	 * subscription.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @return the streaming {@link Response} with output.
	 * @throws IOException
	 *             When Terraform content cannot be written.
	 */
	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("{subscription:\\d+}/terraform.log")
	public Response getTerraformLog(@PathParam("subscription") final int subscription) throws IOException {
		final Subscription entity = subscriptionResource.checkVisibleSubscription(subscription);
		final File log = toFile(entity, MAIN_LOG);

		// Check there is a log file
		if (log.exists()) {
			final StreamingOutput so = o -> FileUtils.copyFile(toFile(entity, MAIN_LOG), o);
			return Response.ok().entity(so).build();
		}

		// No log file for now
		return Response.status(Status.NOT_FOUND).build();
	}

	/**
	 * Apply (plan, apply, show) the Terraform configuration.
	 * 
	 * @param subscription
	 *            The related subscription.
	 */
	@POST
	@Produces(MediaType.TEXT_HTML)
	@Path("{subscription:\\d+}/terraform")
	public void applyTerraform(@PathParam("subscription") final int subscription) {
		final Subscription entity = subscriptionResource.checkVisibleSubscription(subscription);
		final QuoteVo quote = resource.getConfiguration(entity);

		// Check the provider support the Terraform generation

		final Terraforming terra = getTerraform(entity.getNode());
		log.info("Terraform request for {} ({})", subscription, entity);
		final SecurityContext context = SecurityContextHolder.getContext();

		// The Terraform execution will done into another thread
		Executors.newSingleThreadExecutor().submit(() -> {
			// Restore the context
			log.info("Terraform start for {} ({})", entity.getId(), entity);
			SecurityContextHolder.setContext(context);
			try {
				final File file = applyTerraform(entity, terra, quote);
				log.info("Terraform succeed for {} ({})", entity.getId(), entity);
				return file;
			} catch (final Exception e) {
				// The error is not put in the Terraform logger for security
				log.error("Terraform failed for {}", entity, e);
			}
			return null;
		});
	}

	/**
	 * Prepare the Terraform environment to apply the new environment. Note there is
	 * no concurrency check.
	 */
	protected File applyTerraform(final Subscription entity, final Terraforming terra, final QuoteVo configuration)
			throws IOException, InterruptedException {
		final File logFile = toFile(entity, MAIN_LOG);
		final File tfFile = toFile(entity, "main.tf");

		// Clear the previous generated files
		FileUtils.deleteQuietly(logFile);
		FileUtils.deleteQuietly(tfFile);
		FileOutputStream mainTf = null;
		Writer out = null;
		boolean failed = true;
		try {
			// Start the task
			runner.startTask(entity.getNode().getId(), t -> t.setStep(null));

			// Generate and persist the main Terraform file.
			// This file is isolated from the other subscription, inside the
			// subscription context path
			mainTf = new FileOutputStream(tfFile);

			// Create a log file, erasing the previous one
			out = new FileWriter(logFile);

			// Write the Terraform configuration in the 'main.tf' file
			terra.terraform(mainTf, entity.getId(), configuration);

			// Execute the Terraform commands
			executeTerraform(entity, out, terraformUtils.getTerraformSequence(), terra.commandLineParameters(entity.getId()));
			failed = false;
		} finally {
			IOUtils.closeQuietly(mainTf);
			IOUtils.closeQuietly(out);
			runner.endTask(entity.getNode().getId(), failed);
		}

		return logFile;
	}

	private Terraforming getTerraform(final Node node) {
		return Optional.ofNullable(locator.getResource(node.getId(), Terraforming.class))
				.orElseThrow(() -> new BusinessException("terraform-no-supported", node.getRefined().getId()));
	}

	/**
	 * Execute the given Terraform commands. Note there is no concurrency check for
	 * now.
	 */
	private void executeTerraform(final Subscription subscription, final Writer out, final String[][] commands,
			final String... additionalParameters) throws InterruptedException, IOException {
		final AtomicInteger step = new AtomicInteger(0);
		// Reset the current step
		for (final String[] command : commands) {
			// Next step, another transaction
			runner.nextStep("service:prov:test:account", t -> t.setStep(TerraformStep.values()[step.get()]));

			final int code = executeTerraform(subscription, out, ArrayUtils.addAll(command, additionalParameters));
			if (code == 0) {
				// Nothing wrong, no change, only useless to go further
				log.info("Terraform paused for {} ({}) : {}", subscription.getId(), subscription, code);
				out.write("Terraform exit code " + code + " -> no need to continue");
				break;
			}
			if (code != 2) {
				// Something goes wrong
				log.error("Terraform failed for {} ({}) : {}", subscription.getId(), subscription, code);
				out.write("Terraform exit code " + code + " -> aborted");
				throw new BusinessException("aborted");
			}
			out.flush();

			// Code is correct, proceed the next command
			step.incrementAndGet();
		}
	}

	/**
	 * Execute the given Terraform command arguments
	 */
	private int executeTerraform(final Subscription subscription, final Writer out, final String[] command)
			throws InterruptedException, IOException {
		final ProcessBuilder builder = terraformUtils.newBuilder(command);
		builder.redirectErrorStream(true);
		// TODO Subscription identifier is implicit starting from API 1.0.9
		builder.directory(toFile(subscription, MAIN_LOG).getParentFile());
		final Process process = builder.start();
		IOUtils.copy(process.getInputStream(), out, StandardCharsets.UTF_8);

		// Wait and get the code
		int code = process.waitFor();
		out.flush();
		return code;
	}

	/**
	 * Check the subscription is visible, then check the associated provider support
	 * Terraform generation and return the corresponding {@link Terraforming}
	 * instance.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @return {@link Terraforming} instance of this subscription. Never
	 *         <code>null</code>.
	 */
	private Terraforming getTerraform(final int subscription) {
		// Check the provider support the Terraform generation
		return getTerraform(subscriptionResource.checkVisibleSubscription(subscription).getNode());
	}

	/**
	 * Return the file reference from the given subscription.
	 */
	protected File toFile(final Subscription subscription, final String file) throws IOException {
		// TODO Subscription identifier is implicit starting from API 1.0.9
		return PluginsClassLoader.getInstance().toFile(subscription, subscription.getId().toString(), file);
	}
}
