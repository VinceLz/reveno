package org.reveno.atp.core.repository;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.reveno.atp.api.domain.RepositoryData;
import org.reveno.atp.api.domain.WriteableRepository;
import org.reveno.atp.core.api.TxRepository;
import org.reveno.atp.utils.MapUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("unchecked")
public class SnapshotBasedModelRepository implements TxRepository {

	@Override
	public <T> T store(long entityId, T entity) {
		return store(entityId, (Class<T>)entity.getClass(), entity);
	}

	@Override
	public <T> T store(long entityId, Class<? super T> type, T entity) {
		checkAndStore(entityId, type);
		return repository.store(entityId, type, entity);
	}

	@Override
	public Object remove(Class<?> entityClass, long entityId) {
		checkAndStore(entityId, (Class<Object>)entityClass);
		return repository.remove(entityClass, entityId);
	}

	@Override
	public void load(Map<Class<?>, Map<Long, Object>> map) {
		repository.load(map);
	}

	@Override
	public <T> Optional<T> get(Class<T> entityType, long id) {
		return repository.get(entityType, id);
	}

	@Override
	public RepositoryData getData() {
		return repository.getData();
	}

	@Override
	public Map<Long, Object> getEntities(Class<?> entityType) {
		return repository.getEntities(entityType);
	}

	@Override
	public void begin() {
		isTransaction = true;
		snapshotted.forEach((k,v) -> v.clear());
		added.forEach((k,v) -> v.clear());
	}

	@Override
	public void commit() {
		isTransaction = false;
	}

	@Override
	public void rollback() {
		added.forEach((k, v) -> v.forEach(id -> repository.remove(k, id)));
		snapshotted.forEach((k,v) -> v.forEach((id, e) -> repository.store(id, (Class<Object>)k, e)));
		isTransaction = false;
	}
	
	@Override
	public Set<Class<?>> getEntityTypes() {
		return repository.getEntityTypes();
	}
	
	protected <T> void checkAndStore(long entityId, Class<? super T> type) {
		if (isTransaction) {
			LongOpenHashSet entityAdded = added.get(type);
			if (entityAdded.contains(entityId)) 
				return;
			
			Optional<T> oldT = repository.get((Class<T>)type, entityId);
			Long2ObjectOpenHashMap<Object> data = snapshotted.get(type);
			
			if (!oldT.isPresent()) {
				entityAdded.add(entityId);
			} else if (!data.containsKey(entityId)) {
				data.put(entityId, oldT.get());
			}
		}
	}

	public SnapshotBasedModelRepository(WriteableRepository repository) {
		this.repository = repository;
	}
	
	protected Map<Class<?>, Long2ObjectOpenHashMap<Object>> snapshotted = MapUtils.fastRepo();
	protected final Map<Class<?>, LongOpenHashSet> added = MapUtils.fastSetRepo();
	protected final WriteableRepository repository;
	protected volatile boolean isTransaction = false;
	
}
