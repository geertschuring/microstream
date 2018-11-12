package net.jadoth.com;

import static net.jadoth.X.notNull;

import net.jadoth.persistence.types.PersistenceFoundation;
import net.jadoth.persistence.types.PersistenceManager;
import net.jadoth.persistence.types.PersistenceTypeDictionaryCompiler;
import net.jadoth.persistence.types.PersistenceTypeDictionaryView;
import net.jadoth.persistence.types.PersistenceTypeDictionaryViewProvider;


public interface ComPersistenceAdaptor<C> extends PersistenceTypeDictionaryViewProvider
{
	/**
	 * Might return the same instance of all connections or the same for every unique client or a new instance on
	 * every call. Depends on the use-case.<br>
	 * The persistence medium type used by the persistence manager is irrelevant on the com-level, hence the "?".
	 * 
	 * 
	 * @param connection
	 * @return
	 */
	public default PersistenceManager<?> providePersistenceManager(final C connection)
	{
		return this.providePersistenceFoundation(connection)
			.createPersistenceManager()
		;
	}
	
	public default PersistenceTypeDictionaryCompiler provideTypeDictionaryCompiler()
	{
		return this.providePersistenceFoundation(null)
			.getTypeDictionaryCompiler()
		;
	}
	
	/**
	 * Provides a {@link PersistenceFoundation} instance prepared for the passed connection instance.
	 * The passed connection instance might be null, in which case the returned foundation instance
	 * can only be used for general, non-communication-related usage.<p>
	 * See {@link #providePersistenceManager(C)} with a passed non-null connection instance.<br>
	 * See {@link #provideTypeDictionaryCompiler(C)} with a passed null connection instance.
	 * 
	 * @param connection
	 * @return
	 * 
	 * @see #providePersistenceManager(C)
	 * @see #provideTypeDictionaryCompiler()
	 */
	public PersistenceFoundation<?, ?> providePersistenceFoundation(C connection);
	
	
	
	public static <C> ComPersistenceAdaptor.Implementation<C> New(
		final PersistenceFoundation<?, ?> persistenceFoundation
	)
	{
		return new ComPersistenceAdaptor.Implementation<>(
			notNull(persistenceFoundation)
		);
	}
	
	public final class Implementation<C> implements ComPersistenceAdaptor<C>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final PersistenceFoundation<?, ?> persistenceFoundation;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Implementation(final PersistenceFoundation<?, ?> persistenceFoundation)
		{
			super();
			this.persistenceFoundation = persistenceFoundation;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final PersistenceFoundation<?, ?> providePersistenceFoundation(final C connection)
		{
			return this.persistenceFoundation;
		}
		
		@Override
		public final PersistenceTypeDictionaryView provideTypeDictionary()
		{
			// initialization is checked to be done only once.
			return this.persistenceFoundation.getTypeHandlerManager()
				.initialize()
				.typeDictionary()
				.view()
			;
		}
		
	}
	
}