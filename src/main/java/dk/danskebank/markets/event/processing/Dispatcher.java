package dk.danskebank.markets.event.processing;

public interface Dispatcher {
	/**
	 * <p>Dispatch an event for processing by one or more handlers.
	 * <p><b>Contract:</b> The event must be effectively immutable when dispatched. Receivers of the event
	 * are free to store it (and therefore mutable events cannot be pooled by the dispatcher.)
	 * @param event The event to dispatch.
	 */
	void dispatch(Object event);
}
