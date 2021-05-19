/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvFunctionPrice;
import org.ligoj.app.plugin.prov.model.ProvFunctionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvFunctionPrice} repository.
 */
public interface ProvFunctionPriceRepository extends BaseProvTermPriceRepository<ProvFunctionType, ProvFunctionPrice> {

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The required CPU.
	 * @param ram         The required RAM in GiB.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param globalRate  Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query("""
			SELECT ip,
			 ((ip.cost
			  +(CEIL(CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END /ip.incrementCpu) * ip.incrementCpu * ip.costCpu)
			  +(CEIL(
			    CASE WHEN (ip.minRamRatio IS NULL)
			  	THEN (CASE WHEN (ip.minRam > :ram) THEN ip.minRam ELSE :ram END)
			  	ELSE (CASE WHEN ((CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END * ip.minRamRatio)  > :ram)
			  	 	  THEN (CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END * ip.minRamRatio)
			  	 	  ELSE :ram
			  	 	  END)
			  	END /ip.incrementRam) * ip.incrementRam * ip.costRam)
			 )
			 * (CASE WHEN ip.period = 0 THEN :globalRate ELSE (ip.period * CEIL(:duration/ip.period)) END)) AS totalCost,
			 ((ip.cost
			  +(CEIL(CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END /ip.incrementCpu) * ip.incrementCpu * ip.costCpu)
			  +(CEIL(
			    CASE WHEN (ip.minRamRatio IS NULL)
			  	THEN (CASE WHEN (ip.minRam > :ram) THEN ip.minRam ELSE :ram END)
			  	ELSE (CASE WHEN ((CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END * ip.minRamRatio)  > :ram)
			  	 	  THEN (CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END * ip.minRamRatio)
			  	 	  ELSE :ram
			  	 	  END)
			  	END /ip.incrementRam) * ip.incrementRam * ip.costRam)
			 )
			 * (CASE WHEN ip.period = 0 THEN :rate ELSE 1.0 END)) AS monthlyCost
			 FROM #{#entityName} ip WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NOT NULL
			  AND (ip.maxCpu IS NULL or ip.maxCpu >=:cpu)
			  AND (ip.maxRam IS NULL OR ip.maxRam >=:ram)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			  AND (ip.maxRamRatio IS NULL OR (CEIL(CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END * ip.maxRamRatio) <= :ram))
			  ORDER BY totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicPrice(List<Integer> types, List<Integer> terms, double cpu, double ram,
			int location, double rate, double globalRate, double duration, double initialCost, Pageable pageable);

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query("""
			SELECT ip,
			 CASE
			  WHEN ip.period = 0 THEN (ip.cost * :rate * :duration)
			  ELSE (ip.costPeriod * CEIL(:duration/ip.period)) END AS totalCost,
			 CASE
			  WHEN ip.period = 0 THEN (ip.cost * :rate)
			  ELSE ip.cost END AS monthlyCost
			 FROM #{#entityName} ip  WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NULL
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			  ORDER BY totalCost ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestPrice(List<Integer> types, List<Integer> terms, int location, double rate, double duration,
			double initialCost, Pageable pageable);

}