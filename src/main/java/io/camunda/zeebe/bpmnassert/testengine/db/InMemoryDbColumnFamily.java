package io.camunda.zeebe.bpmnassert.testengine.db;

import io.camunda.zeebe.db.*;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

final class InMemoryDbColumnFamily<
        ColumnFamilyNames extends Enum<ColumnFamilyNames>,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final ColumnFamilyNames columnFamily;
  private final TransactionContext context;
  private final KeyType keyInstance;
  private final ValueType valueInstance;

  private final InMemoryDbColumnFamilyIterationContext iterationContext;

  InMemoryDbColumnFamily(
      final ColumnFamilyNames columnFamily,
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.columnFamily = columnFamily;
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;

    iterationContext = new InMemoryDbColumnFamilyIterationContext(columnFamily.ordinal());
  }

  private void ensureInOpenTransaction(
      final TransactionContext context, final inMemoryDbStateOperation operation) {
    context.runInTransaction(
        () -> operation.run((InMemoryDbState) context.getCurrentTransaction()));
  }

  @Override
  public void put(final KeyType key, final ValueType value) {
    ensureInOpenTransaction(
        context, state -> state.put(new FullyQualifiedKey(columnFamily, key), value));
  }

  @Override
  public ValueType get(final KeyType key) {
    final AtomicReference<DirectBuffer> valueBufferRef = new AtomicReference<>(null);

    ensureInOpenTransaction(context, state -> valueBufferRef.set(getValue(state, key)));

    final DirectBuffer valueBuffer = valueBufferRef.get();

    if (valueBuffer != null) {
      valueInstance.wrap(valueBuffer, 0, valueBuffer.capacity());
      return valueInstance;
    }

    return null;
  }

  private DirectBuffer getValue(final InMemoryDbState state, final DbKey key) {
    final FullyQualifiedKey fullyQualifiedKey = new FullyQualifiedKey(columnFamily, key);
    final byte[] value = state.get(fullyQualifiedKey);

    if (value != null) {
      return BufferUtil.wrapArray(value);
    } else {
      return null;
    }
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    forEach(context, (key, value) -> consumer.accept(value));
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    forEach(context, consumer);
  }

  private void forEach(
      final TransactionContext context, final BiConsumer<KeyType, ValueType> consumer) {
    whileEqualPrefix(context, keyInstance, valueInstance, consumer);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileTrue(context, visitor);
  }

  private void whileTrue(
      final TransactionContext context, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, DbNullKey.INSTANCE, keyInstance, valueInstance, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, keyInstance, valueInstance, new Visitor<>(visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  private void whileEqualPrefix(
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> consumer) {
    whileEqualPrefix(
        context, DbNullKey.INSTANCE, keyInstance, valueInstance, new Visitor<>(consumer));
  }

  private void whileEqualPrefix(
      final TransactionContext context,
      final DbKey prefix,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    iterationContext.withPrefixKey(
        prefix,
        prefixKey ->
            ensureInOpenTransaction(
                context,
                state -> {
                  final byte[] prefixKeyBytes = prefixKey.toBytes();
                  final Iterator<Map.Entry<Bytes, Bytes>> iterator =
                      state.newIterator().seek(prefixKeyBytes, prefixKeyBytes.length).iterate();

                  while (iterator.hasNext()) {
                    final Map.Entry<Bytes, Bytes> entry = iterator.next();

                    final byte[] keyBytes = entry.getKey().toBytes();
                    if (!BufferUtil.startsWith(
                        prefixKeyBytes, 0, prefixKeyBytes.length, keyBytes, 0, keyBytes.length)) {
                      continue;
                    }

                    final DirectBuffer keyViewBuffer =
                        FullyQualifiedKey.wrapKey(entry.getKey().toBytes());

                    keyInstance.wrap(keyViewBuffer, 0, keyViewBuffer.capacity());

                    final DirectBuffer valueViewBuffer =
                        BufferUtil.wrapArray(entry.getValue().toBytes());
                    valueInstance.wrap(valueViewBuffer, 0, valueViewBuffer.capacity());

                    final boolean shouldVisitNext = visitor.visit(keyInstance, valueInstance);

                    if (!shouldVisitNext) {
                      return;
                    }
                  }
                }));
  }

  @Override
  public void delete(final KeyType key) {
    final FullyQualifiedKey fullyQualifiedKey = new FullyQualifiedKey(columnFamily, key);
    ensureInOpenTransaction(context, state -> state.delete(fullyQualifiedKey));
  }

  @Override
  public boolean exists(final KeyType key) {
    final AtomicBoolean exists = new AtomicBoolean(true);

    ensureInOpenTransaction(context, state -> exists.set(getValue(state, key) != null));

    return exists.get();
  }

  @Override
  public boolean isEmpty() {
    final AtomicBoolean isEmpty = new AtomicBoolean(true);
    whileEqualPrefix(
        DbNullKey.INSTANCE,
        (key, value) -> {
          isEmpty.set(false);
          return false;
        });

    return isEmpty.get();
  }

  private static final class Visitor<KeyType extends DbKey, ValueType extends DbValue>
      implements KeyValuePairVisitor<KeyType, ValueType> {

    private final BiConsumer<KeyType, ValueType> delegate;

    private Visitor(final BiConsumer<KeyType, ValueType> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean visit(final KeyType key, final ValueType value) {
      delegate.accept(key, value);
      return true;
    }
  }
}