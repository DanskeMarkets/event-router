package dk.danskebank.markets.event.routing;

import dk.danskebank.markets.event.processing.Dispatcher;

/**
 * Classes implementing this interface will have a Dispatcher set as part of the EventRouter's build method.
 */
public interface UsesDispatcher {
	/**
	 * Sets the dispatcher for the implementing class to use for dispatching.
	 * @param dispatcher The dispatcher.
	 */
	void setDispatcher(Dispatcher dispatcher);
}
