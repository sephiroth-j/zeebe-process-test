package io.camunda.testing.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.testing.filters.StreamFilter;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.SoftAssertions;
import org.camunda.community.eze.RecordStreamSource;

public class ProcessInstanceAssert
    extends AbstractAssert<ProcessInstanceAssert, ProcessInstanceEvent> {

  private RecordStreamSource recordStreamSource;

  public ProcessInstanceAssert(
      final ProcessInstanceEvent actual, final RecordStreamSource recordStreamSource) {
    super(actual, ProcessInstanceAssert.class);
    this.recordStreamSource = recordStreamSource;
  }

  /**
   * Verifies the expectation that the process instance is started. This will also be true when the
   * process has been completed or terminated.
   *
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isStarted() {
    final boolean isStarted =
        StreamFilter.processInstance(recordStreamSource)
            .withProcessInstanceKey(actual.getProcessInstanceKey())
            .withRejectionType(RejectionType.NULL_VAL)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnElementType(BpmnElementType.PROCESS)
            .stream()
            .findFirst()
            .isPresent();

    assertThat(isStarted)
        .withFailMessage("Process with key %s was not started", actual.getProcessInstanceKey())
        .isTrue();

    return this;
  }

  /**
   * Verifies the expectation that the process instance is active.
   *
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isActive() {
    final boolean isActive =
        StreamFilter.processInstance(recordStreamSource)
            .withProcessInstanceKey(actual.getProcessInstanceKey())
            .withRejectionType(RejectionType.NULL_VAL)
            .withBpmnElementType(BpmnElementType.PROCESS)
            .stream()
            .noneMatch(
                record ->
                    record.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                        || record.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED);

    assertThat(isActive)
        .withFailMessage("Process with key %s is not active", actual.getProcessInstanceKey())
        .isTrue();

    return this;
  }

  /**
   * Verifies the expectation that the process instance is completed.
   *
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isCompleted() {
    assertThat(isProcessInstanceCompleted())
        .withFailMessage("Process with key %s was not completed", actual.getProcessInstanceKey())
        .isTrue();
    return this;
  }

  /**
   * Verifies the expectation that the process instance is not completed.
   *
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isNotCompleted() {
    assertThat(isProcessInstanceCompleted())
        .withFailMessage("Process with key %s was completed", actual.getProcessInstanceKey())
        .isFalse();
    return this;
  }

  /**
   * Checks if a process instance has been completed
   *
   * @return boolean indicating whether the process instance has been completed
   */
  private boolean isProcessInstanceCompleted() {
    return StreamFilter.processInstance(recordStreamSource)
        .withProcessInstanceKey(actual.getProcessInstanceKey())
        .withRejectionType(RejectionType.NULL_VAL)
        .withBpmnElementType(BpmnElementType.PROCESS)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .stream()
        .findFirst()
        .isPresent();
  }

  /**
   * Verifies the expectation that the process instance is terminated.
   *
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isTerminated() {
    assertThat(isProcessInstanceTerminated())
        .withFailMessage("Process with key %s was not terminated", actual.getProcessInstanceKey())
        .isTrue();
    return this;
  }

  /**
   * Verifies the expectation that the process instance is not terminated.
   *
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isNotTerminated() {
    assertThat(isProcessInstanceTerminated())
        .withFailMessage("Process with key %s was terminated", actual.getProcessInstanceKey())
        .isFalse();
    return this;
  }

  /**
   * Checks if a process instance has been terminated
   *
   * @return boolean indicating whether the process instance has been terminated
   */
  private boolean isProcessInstanceTerminated() {
    return StreamFilter.processInstance(recordStreamSource)
        .withProcessInstanceKey(actual.getProcessInstanceKey())
        .withRejectionType(RejectionType.NULL_VAL)
        .withBpmnElementType(BpmnElementType.PROCESS)
        .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .stream()
        .findFirst()
        .isPresent();
  }

  /**
   * Verifies the expectation that the process instance has passed an element with a specific
   * element id exactly one time.
   *
   * @param elementId The id of the element
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert hasPassedElement(final String elementId) {
    return hasPassedElement(elementId, 1);
  }

  /**
   * Verifies the expectation that the process instance has not passed an element with a specific
   * element id.
   *
   * @param elementId The id of the element
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert hasNotPassedElement(final String elementId) {
    return hasPassedElement(elementId, 0);
  }

  /**
   * Verifies the expectation that the process instance has passed an element with a specific
   * element id exactly N amount of times.
   *
   * @param elementId The id of the element
   * @param times The amount of times the element should be passed
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert hasPassedElement(final String elementId, final int times) {
    final long count =
        StreamFilter.processInstance(recordStreamSource)
            .withProcessInstanceKey(actual.getProcessInstanceKey())
            .withRejectionType(RejectionType.NULL_VAL)
            .withElementId(elementId)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .stream()
            .count();

    assertThat(count)
        .withFailMessage("Expected element with id %s to be passed %s times", elementId, times)
        .isEqualTo(times);

    return this;
  }

  /**
   * Verifies the expectation that the process instance has passed the given elements in order.
   *
   * @param elementIds The element ids
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert hasPassedElementInOrder(final String... elementIds) {
    final List<String> foundElementRecords =
        StreamFilter.processInstance(recordStreamSource)
            .withProcessInstanceKey(actual.getProcessInstanceKey())
            .withRejectionType(RejectionType.NULL_VAL)
            .withElementIdIn(elementIds)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .stream()
            .map(Record::getValue)
            .map(ProcessInstanceRecordValue::getElementId)
            .collect(Collectors.toList());

    assertThat(foundElementRecords)
        .describedAs("Ordered elements")
        .isEqualTo(Arrays.asList(elementIds));

    return this;
  }

  /**
   * Verifies the expectation that the process instance is currently waiting at one or more
   * specified elements.
   *
   * @param elementIds The ids of the elements
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isWaitingAtElement(final String... elementIds) {
    final Set<String> elementsInWaitState = getElementsInWaitState();
    assertThat(elementsInWaitState).containsAll(Arrays.asList(elementIds));
    return this;
  }

  /**
   * Verifies the expectation that the process instance is currently not waiting at one or more
   * specified elements.
   *
   * @param elementIds The ids of the elements
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isNotWaitingAtElement(final String... elementIds) {
    final Set<String> elementsInWaitState = getElementsInWaitState();
    assertThat(elementsInWaitState).doesNotContainAnyElementsOf(Arrays.asList(elementIds));
    return this;
  }

  /**
   * Gets the elements that are currently in a waiting state.
   *
   * @return set containing the element ids of the elements in a waiting state
   */
  private Set<String> getElementsInWaitState() {
    final Set<String> elementsInWaitState = new HashSet<>();
    StreamFilter.processInstance(recordStreamSource)
        .withProcessInstanceKey(actual.getProcessInstanceKey())
        .withRejectionType(RejectionType.NULL_VAL)
        .withoutBpmnElementType(BpmnElementType.PROCESS)
        .stream()
        .collect(
            Collectors.toMap(
                record ->
                    String.format(
                        "%s-%s",
                        record.getValue().getElementId(), record.getValue().getFlowScopeKey()),
                record -> record,
                (existing, replacement) -> replacement))
        .forEach(
            (key, record) -> {
              if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATED)) {
                elementsInWaitState.add(record.getValue().getElementId());
              }
            });
    return elementsInWaitState;
  }

  /**
   * Verifies the expectation that the process instance is currently waiting at the specified
   * elements, and not at any other element.
   *
   * @param elementIdsVarArg The ids of the elements
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isWaitingExactlyAtElements(final String... elementIdsVarArg) {
    final List<String> elementIds = Arrays.asList(elementIdsVarArg);
    final Set<String> elementsInWaitState = getElementsInWaitState();
    final List<String> wrongfullyWaitingElementIds = new ArrayList<>();
    final List<String> wrongfullyNotWaitingElementIds = new ArrayList<>();

    StreamFilter.processInstance(recordStreamSource)
        .withProcessInstanceKey(actual.getProcessInstanceKey())
        .withRejectionType(RejectionType.NULL_VAL)
        .withoutBpmnElementType(BpmnElementType.PROCESS)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .stream()
        .map(Record::getValue)
        .map(ProcessInstanceRecordValue::getElementId)
        .distinct()
        .forEach(
            id -> {
              final boolean shouldBeWaitingAtElement = elementIds.contains(id);
              final boolean isWaitingAtElement = elementsInWaitState.contains(id);
              if (shouldBeWaitingAtElement && !isWaitingAtElement) {
                wrongfullyNotWaitingElementIds.add(id);
              } else if (!shouldBeWaitingAtElement && isWaitingAtElement) {
                wrongfullyWaitingElementIds.add(id);
              }
            });

    final SoftAssertions softly = new SoftAssertions();
    softly
        .assertThat(wrongfullyWaitingElementIds.isEmpty())
        .withFailMessage(
            "Process with key %s is waiting at element(s) with id(s) %s",
            actual.getProcessInstanceKey(), String.join(", ", wrongfullyWaitingElementIds))
        .isTrue();
    softly
        .assertThat(wrongfullyNotWaitingElementIds.isEmpty())
        .withFailMessage(
            "Process with key %s is not waiting at element(s) with id(s) %s",
            actual.getProcessInstanceKey(), String.join(", ", wrongfullyNotWaitingElementIds))
        .isTrue();
    softly.assertAll();

    return this;
  }

  /**
   * Verifies the expectation that the process instance is currently waiting to receive one or more
   * specified messages.
   *
   * @param messageNames Names of the messages
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isWaitingForMessage(final String... messageNames) {
    final Set<String> openMessageSubscriptions = getOpenMessageSubscriptions();
    assertThat(openMessageSubscriptions).containsAll(Arrays.asList(messageNames));
    return this;
  }

  /**
   * Verifies the expectation that the process instance is currently not waiting to receive one or
   * more specified messages.
   *
   * @param messageNames Names of the messages
   * @return this {@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert isNotWaitingForMessage(final String... messageNames) {
    final Set<String> openMessageSubscriptions = getOpenMessageSubscriptions();
    assertThat(openMessageSubscriptions).doesNotContainAnyElementsOf(Arrays.asList(messageNames));
    return this;
  }

  /**
   * Gets the currently open message subscription from the record stream source
   *
   * @return set containing the message names of the open message subscriptions
   */
  private Set<String> getOpenMessageSubscriptions() {
    final Set<String> openMessageSubscriptions = new HashSet<>();
    StreamFilter.processMessageSubscription(recordStreamSource)
        .withProcessInstanceKey(actual.getProcessInstanceKey())
        .withRejectionType(RejectionType.NULL_VAL)
        .stream()
        .collect(
            Collectors.toMap(
                record -> record.getValue().getElementInstanceKey(),
                record -> record,
                (existing, replacement) -> replacement))
        .forEach(
            (key, record) -> {
              if (record.getIntent().equals(ProcessMessageSubscriptionIntent.CREATING)
                  || record.getIntent().equals(ProcessMessageSubscriptionIntent.CREATED)) {
                openMessageSubscriptions.add(record.getValue().getMessageName());
              }
            });
    return openMessageSubscriptions;
  }

  /**
   * Verifies the process instance has a variable with the specified name
   *
   * @param name The name of the variable
   * @return this ${@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert hasVariable(final String name) {
    final Map<String, String> variables = getProcessInstanceVariables();
    return assertVariableInMapOfVariables(name, variables);
  }

  /**
   * Assert that the given variable name is a key in the given map of variables.
   *
   * <p>This assertion has been extracted from the method ${@link #hasVariable(String)} so that the
   * method ${@link #hasVariableWithValue(String, String)} could reuse it without having to traverse
   * the record stream to collect the variables a second time.
   *
   * @param name The name of the variable
   * @param variables The map of variables
   * @return this ${@link ProcessInstanceAssert}
   */
  private ProcessInstanceAssert assertVariableInMapOfVariables(
      final String name, final Map<String, String> variables) {
    assertThat(variables)
        .withFailMessage(
            "Process with key %s does not contain variable with name `%s`. Available variables are: %s",
            actual.getProcessInstanceKey(), name, variables.keySet())
        .containsKey(name);
    return this;
  }

  /**
   * Verifies the process instance has a variable with a specific value.
   *
   * @param name The name of the variable
   * @param value The value of the variable
   * @return this ${@link ProcessInstanceAssert}
   */
  public ProcessInstanceAssert hasVariableWithValue(final String name, final String value) {
    final ZeebeObjectMapper mapper = new ZeebeObjectMapper();
    final String mappedValue = mapper.toJson(value);
    final Map<String, String> variables = getProcessInstanceVariables();

    assertVariableInMapOfVariables(name, variables);
    assertThat(variables)
        .withFailMessage(
            "The variable '%s' does not have the expected value. The value passed in"
                + " ('%s') is internally mapped to a JSON String that yields '%s'. However, the "
                + "actual value (as JSON String) is '%s.",
            name, value, mappedValue, variables.get(name))
        .containsEntry(name, mappedValue);

    return this;
  }

  /**
   * Returns a Map of variables that belong to this process instance
   *
   * @return map of variables
   */
  private Map<String, String> getProcessInstanceVariables() {
    return StreamFilter.variable(recordStreamSource)
        .withProcessInstanceKey(actual.getProcessInstanceKey())
        .withRejectionType(RejectionType.NULL_VAL)
        .stream()
        .map(Record::getValue)
        .collect(Collectors.toMap(VariableRecordValue::getName, VariableRecordValue::getValue));
  }
}