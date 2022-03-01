package dk.danskebank.markets.event.routing;

import dk.danskebank.markets.event.processing.Dispatcher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.logging.log4j.Level;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * <p>Create an {@code EventRouter.Builder} or {@code EventRouter.BreadthFirstBuilder} to build the {@code EventRouter}.
 *
 * <p>Example:
 * <pre>{@code
 *     Dispatcher eventRouter = new EventRouter.Builder()
 *         .route(   Start.class).to(priceHandler, tradeHandler)
 *         .route(    Stop.class).to(tradeHandler, priceHandler)
 *         .route(NewTrade.class).to(tradeHandler)
 *         .route(NewPrice.class).to(priceHandler)
 *         .route(    Tick.class).to(priceHandler)
 *     .build();
 * }
 * </pre>
 */
public abstract class EventRouter implements Dispatcher {
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static class DepthFirst extends EventRouter {
		private final Dispatcher dispatcher;

		@Override public void dispatch(Object event) {
			dispatcher.dispatch(event);
		}
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static class BreadthFirst extends EventRouter {
		private final Dispatcher dispatcher;
		private final Queue<Object> queuedEvents = new LinkedList<>();

		@Override public void dispatch(Object event) {
			val isInitialDispatch = queuedEvents.isEmpty();
			queuedEvents.add(event);
			if (isInitialDispatch) {
				while (!queuedEvents.isEmpty()) {
					val nextEvent = queuedEvents.peek();
					dispatcher.dispatch(nextEvent);
					queuedEvents.remove();
				}
			}
		}
	}

	public static class BreadthFirstBuilder extends Builder {
		/** Creates a new Builder with INFO as default logging level and breadth-first dispatching. */
		public BreadthFirstBuilder() {}

		@Override public EventRouter build() {
			val implementation      = new ArrayBasedEventRouterDispatcher(eventToHandlers);
			EventRouter eventRouter = new BreadthFirst(implementation);
			handlersUsingDispatcher.forEach(handler -> handler.setDispatcher(eventRouter));
			return eventRouter;
		}
	}

	public static class Builder {
		protected final Map<Class<?>, List<Handler>> eventToHandlers = new LinkedHashMap<>(); // To get stable ordering.
		protected final Set<UsesDispatcher> handlersUsingDispatcher  = new HashSet<>();

		private final Level defaultLogLevel;

		/** Creates a new Builder with INFO as default logging level and depth-first dispatching. */
		public Builder() {
			this(Level.INFO);
		}

		public Builder(Level defaultLogLevel) {
			this.defaultLogLevel = defaultLogLevel;
		}

		public <E> RouteEvent<E> route(Class<E> eventClass) {
			return new RouteEvent<>(eventClass);
		}

		public EventRouter build() {
			val implementation      = new ArrayBasedEventRouterDispatcher(eventToHandlers);
			EventRouter eventRouter = new DepthFirst(implementation);
			handlersUsingDispatcher.forEach(handler -> handler.setDispatcher(eventRouter));
			return eventRouter;
		}

		public class RouteEvent<E> {
			private final Class<E> eventClass;

			public RouteEvent(Class<E> eventClass) {
				this.eventClass = requireNonNull(eventClass);
				if (eventClass.isInterface()) {
					throw new IllegalArgumentException("Event must be a class. "+eventClass.getSimpleName()+" is an interface.");
				}
				if (eventToHandlers.containsKey(eventClass)) {
					throw new IllegalStateException("There's already a routing for "+eventClass.getSimpleName());
				}
				eventToHandlers.put(eventClass, new ArrayList<>());
			}

			public WithDefaultLogLevel to(Object handler, Object... handlers) {
				return new WithDefaultLogLevel(handler, handlers);
			}

			private void add(Object handler, Level level) {
				val handlerClass = handler.getClass();
				if (handler instanceof UsesDispatcher) {
					handlersUsingDispatcher.add((UsesDispatcher)handler);
				}

				var found = false;
				for (val method: handlerClass.getMethods()) {
					Class<?>[] parameterTypes = method.getParameterTypes();
					if (parameterTypes.length != 1 || method.isSynthetic()) continue;
					if (!isPublic(method)) continue;

					val paramClass      = parameterTypes[0];
					val eventInterfaces = eventClass.getInterfaces();
					val superTypes      = new Class[eventInterfaces.length + 1];

					superTypes[0] = eventClass;
					System.arraycopy(eventInterfaces, 0, superTypes, 1, eventInterfaces.length);

					for (var type : superTypes) {
						if (type.equals(paramClass)) {
							if (found) {
								throw new IllegalStateException("Only one method in "+handler.getClass().getSimpleName()+
										" can handle the event type "+type.getSimpleName());
							}
							found = true;
							val handlerList = eventToHandlers.get(eventClass);
							handlerList.add(new Handler(handler, method, level));
							break;
						}
					}
				}
				if (!found) throw new IllegalStateException("No handler method found on "+handler.getClass().getSimpleName()+
						" for event type "+eventClass.getSimpleName());
			}

			public class WithDefaultLogLevel {
				private final Object handler;
				private final Object[] handlers;

				public WithDefaultLogLevel(Object handler, Object... handlers) {
					this.handler  = requireNonNull(handler);
					this.handlers = requireNonNull(handlers);
				}

				public Builder logAs(Level level) {
					addHandlers(level);
					return Builder.this;
				}

				public <E2> RouteEvent<E2> route(Class<E2> eventClass) {
					addHandlers(defaultLogLevel);
					return new RouteEvent<>(eventClass);
				}

				public EventRouter build() {
					addHandlers(defaultLogLevel);
					return Builder.this.build();
				}

				private void addHandlers(Level level) {
					add(handler, level);
					for (val h: handlers) {
						add(h, level);
					}
				}
			}
		}

		private boolean isPublic(Method method) {
			val modifiers = method.getModifiers();
			return (modifiers & Modifier.PUBLIC) != 0;
		}
	}
}
