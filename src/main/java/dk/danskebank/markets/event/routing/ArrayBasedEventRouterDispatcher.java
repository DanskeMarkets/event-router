package dk.danskebank.markets.event.routing;

import dk.danskebank.markets.event.processing.Dispatcher;
import lombok.val;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

class ArrayBasedEventRouterDispatcher implements Dispatcher {
	private final Class[] eventTypes;
	private final Handler[][] handlers;

	ArrayBasedEventRouterDispatcher(Map<Class<?>, List<Handler>> eventToHandlers) {
		eventTypes = new Class[eventToHandlers.size()];
		handlers   = new Handler[eventToHandlers.size()][];

		int nextIndex = 0;

		for (val entry: eventToHandlers.entrySet()) {
			val eventType   = entry.getKey();
			val handlerList = entry.getValue();

			eventTypes[nextIndex] = eventType;
			val handlerArray      = new Handler[handlerList.size()];
			handlers[nextIndex]   = handlerArray;

			for (int i = 0; i < handlerList.size(); ++i) {
				handlerArray[i] = handlerList.get(i);
			}
			++nextIndex;
		}
	}

	@Override public void dispatch(Object event) {
		requireNonNull(event);
		val clazz = event.getClass();
		int index = -1;
		for (int i = 0; i < eventTypes.length; ++i) {
			if (eventTypes[i].equals(clazz)) {
				index = i;
				break;
			}
		}
		if (index == -1) throw new IllegalArgumentException("No routing for event type: "+clazz.getSimpleName());

		val handlersForEvent = handlers[index];
		for (int i = 0; i < handlersForEvent.length; ++i) {
			handlersForEvent[i].dispatch(event);
		}
	}
}
