package dk.danskebank.markets.event.routing;

import dk.danskebank.markets.event.processing.Dispatcher;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class DefaultEventRouterDispatcher implements Dispatcher {
	private final Map<Class<?>, List<Handler>> eventToHandlers;

	@Override public void dispatch(@NonNull Object event) {
		val clazz = event.getClass();

		val handlers = eventToHandlers.get(clazz);
		if (handlers == null) throw new IllegalArgumentException("No routing for event type: "+clazz.getSimpleName());

		for (val handler: handlers) {
			handler.dispatch(event);
		}
	}
}
