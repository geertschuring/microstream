package net.jadoth.util.matching;

/**
 * Since matching on similarity is a heuristical method, it can be necessary to have a validation callback logic
 * that can ultimately decide on potential matches.
 * 
 * @author TM
 *
 * @param <E>
 */
@FunctionalInterface
public interface MatchValidator<E>
{
	public boolean isValidMatch(
		E      source              ,
		E      target              ,
		double similarity          ,
		int    sourceCandidateCount,
		int    targetCandidateCount
	);
}