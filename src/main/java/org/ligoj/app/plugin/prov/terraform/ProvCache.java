/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.util.function.Function;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;

import org.ligoj.bootstrap.resource.system.cache.CacheManagerAware;
import org.springframework.stereotype.Component;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.config.CacheConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;

/**
 * Provisioning data cache configurations.
 */
@Component
public class ProvCache implements CacheManagerAware {

	@Override
	public void onCreate(final HazelcastCacheManager cacheManager, final Function<String, CacheConfig<?, ?>> provider) {
		cacheManager.createCache("terraform-version", provider.apply("terraform-version"));
		final var cfgTVL = provider.apply("terraform-version-latest")
				.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(Duration.ONE_DAY));
		cacheManager.createCache("terraform-version-latest", cfgTVL);

		final var cfgPL = provider.apply("prov-location")
				.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU)
						.setMaximumSizePolicy(EvictionConfig.MaxSizePolicy.ENTRY_COUNT).setSize(1000));
		cacheManager.createCache("prov-location", cfgPL);
		createCache(cacheManager, provider, "prov-license");
		createCache(cacheManager, provider, "prov-software");
		createCache(cacheManager, provider, "prov-processor");

		// Instance cache configurations
		newCacheConfig(cacheManager, provider, "prov-instance-type");
		newCacheConfig(cacheManager, provider, "prov-instance-type-dyn");
		newCacheConfig(cacheManager, provider, "prov-instance-type-has-dyn");

		// Database cache configurations
		newCacheConfig(cacheManager, provider, "prov-database-type");
		newCacheConfig(cacheManager, provider, "prov-database-type-dyn");
		newCacheConfig(cacheManager, provider, "prov-database-type-has-dyn");
		createCache(cacheManager, provider, "prov-database-engine");
		createCache(cacheManager, provider, "prov-database-edition");
		createCache(cacheManager, provider, "prov-database-license");

		// Container cache configurations
		newCacheConfig(cacheManager, provider, "prov-container-type");
		newCacheConfig(cacheManager, provider, "prov-container-type-dyn");
		newCacheConfig(cacheManager, provider, "prov-container-type-has-dyn");
		createCache(cacheManager, provider, "prov-container-license");

		newCacheConfig(cacheManager, provider, "prov-instance-term");
	}

	private void createCache(final HazelcastCacheManager cacheManager,
			final Function<String, CacheConfig<?, ?>> provider, final String name) {
		cacheManager.createCache(name, provider.apply(name));
	}

	private void newCacheConfig(final HazelcastCacheManager cacheManager,
			final Function<String, CacheConfig<?, ?>> provider, final String name) {
		final var cfgPIT = provider.apply(name)
				.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU)
						.setMaximumSizePolicy(EvictionConfig.MaxSizePolicy.ENTRY_COUNT).setSize(1000));
		cacheManager.createCache(name, cfgPIT);
	}
}
