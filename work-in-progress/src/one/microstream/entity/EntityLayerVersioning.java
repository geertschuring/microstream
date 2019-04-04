package one.microstream.entity;

import one.microstream.collections.EqHashTable;

public class EntityLayerVersioning<E extends Entity<E>, K> extends EntityLayer<E>
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final EntityVersionContext<K> versionContext;
	private final EqHashTable<K, E>       versions     ;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected EntityLayerVersioning(final Entity<E> data, final EntityVersionContext<K> versionContext)
	{
		super(data);
		this.versionContext = versionContext;
		this.versions       = EqHashTable.New(versionContext.equality());
	}
	
	
	
	/* (30.11.2017 TM)TODO:
	 * How to access the versions to delete obsolete versions, shrink the table's storage, etc?
	 * Wouldn't it be more efficient for the context to hold an identity-HashTable of all
	 * current versions of all (registered) versioned entities?
	 * As a positive side-effect, the delegated equality would disappear.
	 * 
	 * But that would create a memory leak, so the central HashTable would have to use weak references.
	 * A downside would be that because of the required locking, a central registry would scale worse
	 * compared to a decentralized entity-intrinsic solution.
	 */
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public synchronized E $data()
	{
		final K versionKey = this.versionContext.currentVersion();
		if(versionKey == null)
		{
			return super.$data();
		}
		
		final E versionedData = this.versions.get(versionKey);
		if(versionedData == null)
		{
			// (30.11.2017 TM)EXCP: proper exception
			throw new RuntimeException("No data for version " + versionKey);
		}
		
		return versionedData;
	}

	@Override
	public synchronized boolean $updateData(final E data)
	{
		final K versionKey = this.versionContext.currentVersion();
		if(versionKey == null)
		{
			super.$updateData(data);
		}
		else
		{
			this.versions.put(versionKey, data);
		}
		
		return true;
	}
	
}