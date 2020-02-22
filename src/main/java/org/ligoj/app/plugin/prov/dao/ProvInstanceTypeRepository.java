/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvInstanceType} repository.
 */
public interface ProvInstanceTypeRepository extends BaseProvInstanceTypeRepository<ProvInstanceType> {

	/**
	 * Return the valid instance types matching the requirements.
	 *
	 * @param node      The node linked to the subscription. Is a node identifier within a provider.
	 * @param cpu       The minimum CPU.
	 * @param ram       The minimum RAM in MB.
	 * @param constant  The optional constant CPU behavior constraint.
	 * @param physical  The optional physical (not virtual) instance type constraint.
	 * @param type      The optional instance type identifier. May be <code>null</code>.
	 * @param processor Optional processor requirement. A <code>LIKE</code> will be used.
	 * @return The matching dynamic instance types.
	 */
	@CacheResult(cacheName = "prov-instance-type-dyn")
	@Query("SELECT id FROM #{#entityName} WHERE                          "
			+ "      (:node = node.id OR :node LIKE CONCAT(node.id,':%'))"
			+ "  AND (:type IS NULL OR id = :type)                       "
			+ "  AND cpu = 0                                             "
			+ "  AND (:constant IS NULL OR constant = :constant)         "
			+ "  AND (:physical IS NULL OR physical = :physical)         "
			+ "  AND (:processor IS NULL                                 "
			+ "   OR (processor IS NOT NULL AND UPPER(processor) LIKE CONCAT('%', CONCAT(UPPER(:processor), '%'))))")
	List<Integer> findDynamicTypes(@CacheKey String node, @CacheKey Boolean constant, @CacheKey Boolean physical,
			@CacheKey Integer type, @CacheKey String processor);

	/**
	 * Return the valid instance types matching the requirements.
	 *
	 * @param node      The node linked to the subscription. Is a node identifier within a provider.
	 * @param cpu       The minimum CPU.
	 * @param ram       The minimum RAM in MB.
	 * @param constant  The optional constant CPU behavior constraint.
	 * @param physical  The optional physical (not virtual) instance type constraint.
	 * @param type      The optional instance type identifier. May be <code>null</code>.
	 * @param processor Optional processor requirement. A <code>LIKE</code> will be used.
	 * @return The matching instance types.
	 */
	@CacheResult(cacheName = "prov-instance-type")
	@Query("SELECT id FROM #{#entityName} WHERE                          "
			+ "      (:node = node.id OR :node LIKE CONCAT(node.id,':%'))"
			+ "  AND (:type IS NULL OR id = :type)                       "
			+ "  AND cpu != 0 AND cpu>= :cpu AND ram>=:ram               "
			+ "  AND (:constant IS NULL OR constant = :constant)         "
			+ "  AND (:physical IS NULL OR physical = :physical)         "
			+ "  AND (:processor IS NULL                                 "
			+ "   OR (processor IS NOT NULL AND UPPER(processor) LIKE CONCAT('%', CONCAT(UPPER(:processor), '%'))))")
	List<Integer> findValidTypes(@CacheKey String node, @CacheKey double cpu, @CacheKey int ram,
			@CacheKey Boolean constant, @CacheKey Boolean physical, @CacheKey Integer type, @CacheKey String processor);
}
