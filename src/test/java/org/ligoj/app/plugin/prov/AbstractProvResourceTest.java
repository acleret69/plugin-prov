/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvTagRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuoteResource;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.support.QuoteTagSupport;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvQuoteInstanceResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public abstract class AbstractProvResourceTest extends AbstractAppTest {

	protected static final double DELTA = 0.01d;

	@Autowired
	protected ProvResource resource;

	protected int subscription;

	@Autowired
	private ProvTagRepository tagRepository;

	@Autowired
	protected ConfigurationResource configuration;

	/**
	 * Prepare test data.
	 * 
	 * @throws IOException When CSV cannot be read.
	 */
	@BeforeEach
	protected void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvStorageType.class, ProvStoragePrice.class,
						ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class,
						ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		clearAllCache();
		configuration.put(ProvResource.USE_PARALLEL, "0");
		updateCost();
	}

	
	/**
	 * Flush the current JPA context and return the configuration of current subscription.
	 * 
	 * @return The configuration of current subscription.
	 */
	protected QuoteVo getConfiguration() {
		return getConfiguration(subscription);
	}

	/**
	 * Flush the current JPA context and return the configuration of given subscription.
	 * 
	 * @param subscription The subscription to get.
	 * @return The configuration of given subscription.
	 */
	protected QuoteVo getConfiguration(final int subscription) {
		em.flush();
		em.clear();
		return resource.getConfiguration(subscription);
	}

	/**
	 * Add two basic tags to the given object.
	 * 
	 * @param vo The object to complete with the new tags.
	 */
	protected void newTags(final QuoteTagSupport vo) {
		List<TagVo> tags = new ArrayList<>();
		final var tag1 = new TagVo();
		tag1.setName("name1");
		tag1.setValue("value1");
		tags.add(tag1);
		final var tag2 = new TagVo();
		tag2.setName("name2");
		tags.add(tag2);
		vo.setTags(tags);
	}

	/**
	 * Check the basics tags are associated to the given resource.
	 * 
	 * @param resource The resource to test.
	 */
	protected void assertTags(final AbstractQuoteResource<?> resource) {
		Assertions.assertTrue(tagRepository
				.findAllBy("configuration.id", resource.getConfiguration().getId(), new String[] { "resource", "type" },
						resource.getId(), resource.getResourceType())
				.stream().allMatch(t -> "name1".equals(t.getName()) && "value1".equals(t.getValue())
						|| "name2".equals(t.getName()) && t.getValue() == null));
	}

	protected QuoteLightVo checkCost(final int subscription, final double min, final double max,
			final boolean unbound) {
		final var status = resource.getSubscriptionStatus(subscription);
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	protected void checkCost(final FloatingCost cost, final double min, final double max, final boolean unbound) {
		Assertions.assertEquals(min, cost.getMin(), DELTA);
		Assertions.assertEquals(max, cost.getMax(), DELTA);
		Assertions.assertEquals(unbound, cost.isUnbound());
	}

	protected void checkCost(final UpdatedCost cost, final double min, final double max, final boolean unbound) {
		checkCost(cost.getTotal(), min, max, unbound);
	}

	protected void updateCost() {
		// Check the cost fully updated and exact actual cost
		final var cost = resource.updateCost(subscription);
		Assertions.assertEquals(4704.758, cost.getMin(), DELTA);
		Assertions.assertEquals(7154.358, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 4704.758, 7154.358, false);
		em.flush();
		em.clear();
	}

}
