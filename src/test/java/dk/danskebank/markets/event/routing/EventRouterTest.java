package dk.danskebank.markets.event.routing;

import dk.danskebank.markets.event.processing.Dispatcher;
import lombok.EqualsAndHashCode;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.slf4j.event.Level.*;

public class EventRouterTest {
	// Event and Handler classes used in the tests.
	interface Event {}

	class Event1 {
		public boolean isImportant() { return true; }
	}
	@EqualsAndHashCode
	class Event2 implements Event {}
	@EqualsAndHashCode
	class Event3 {}

	class Handler1 {
		public void on(Event1 event) { onPrivate(event); }
		private void onPrivate(Event1 event) {} // Private methods are ok.
	}
	class Handler2 { public void on(Event event) {} }
	class Handler3 {// Incorrect handler.
		public void on1(Event1 event) {}
		public void on2(Event1 event) {}
	}
	class Handler4 {
		public void on(Event1 event) {}
		public void on(Event3 event) {}
	}
	class Handler5 implements UsesDispatcher {
		private Dispatcher dispatcher;
		@Override public void setDispatcher(Dispatcher dispatcher) { this.dispatcher = dispatcher; }
		public void on(Event1 event) { dispatcher.dispatch(new Event2()); }
	}
	class Handler6 implements UsesDispatcher {
		private Dispatcher dispatcher;
		@Override public void setDispatcher(Dispatcher dispatcher) { this.dispatcher = dispatcher; }
		public void on(Event2 event) { dispatcher.dispatch(new Event3()); }
	}

	@Test void whenRegisteredInterfaceAsEventThrowsAnException() {
		val handler   = new Handler1();
		val exception = assertThrows(IllegalArgumentException.class, () ->
				new EventRouter.Builder()
						.route(Event.class).to(handler)
					.build()
		);
		assertEquals("Event must be a class. Event is an interface.", exception.getMessage());
	}

	@Test void whenSameEventRegisteredTwiceThrowsAnException() {
		val handler   = new Handler1();
		val exception = assertThrows(IllegalStateException.class, () ->
			new EventRouter.Builder()
				.route(Event1.class).to(handler)
				.route(Event1.class).to(handler)
			.build()
		);
		assertEquals("There's already a routing for Event1", exception.getMessage());
	}

	@Test void whenHandlerIsMissingAWellformedMethodThrowsAnException() {
		val handler   = new Handler1();
		val exception = assertThrows(IllegalStateException.class, () ->
				new EventRouter.Builder()
					.route(Event2.class).to(handler)
				.build()
		);
		assertEquals("No handler method found on Handler1 for event type Event2", exception.getMessage());
	}

	@Test void whenHandlerHasTwoMethodsForSameEventThrowsAnException() {
		val handler   = new Handler3();
		val exception = assertThrows(IllegalStateException.class, () ->
				new EventRouter.Builder()
					.route(Event1.class).to(handler)
				.build()
		);
		assertEquals("Only one method in Handler3 can handle the event type Event1", exception.getMessage());
	}

	@Test void whenDispatchingAnUndefinedObjectThrowsAnException() {
		val handler    = new Handler1();
		val dispatcher = new EventRouter.Builder()
				.route(Event1.class).to(handler)
			.build();
		val exception = assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(new Object()));
		assertEquals("No routing for event type: Object", exception.getMessage());
	}

	@Test void whenDispatchingAnEventItShouldBePassedToRoutedHandlers() {
		val event1     = new Event1();
		val event2     = new Event2();
		val handler1   = mock(Handler1.class);
		val handler2   = mock(Handler2.class);
		val dispatcher = new EventRouter.Builder()
				.route(Event1.class).to(handler1)
				.route(Event2.class).to(handler2)
			.build();
		dispatcher.dispatch(event1);
		dispatcher.dispatch(event2);
		dispatcher.dispatch(event1);

		val inOrder = inOrder(handler1, handler2);
		inOrder.verify(handler1).on(event1);
		inOrder.verify(handler2).on(event2);
		inOrder.verify(handler1).on(event1);
	}


	@Test void whenDispatchingAnEventItShouldBePassedToRoutedHandlersAndLoggingSettingsShouldNotEffectAnything() {
		val event1     = new Event1();
		val event2     = new Event2();
		val handler1   = mock(Handler1.class);
		val handler2   = mock(Handler2.class);
		val dispatcher = new EventRouter.Builder(DEBUG)
				.route(Event1.class).to(handler1).logAs(WARN )
				.route(Event2.class).to(handler2).logAs(ERROR)
				.build();
		dispatcher.dispatch(event1);
		dispatcher.dispatch(event2);
		dispatcher.dispatch(event1);

		val inOrder = inOrder(handler1, handler2);
		inOrder.verify(handler1).on(event1);
		inOrder.verify(handler2).on(event2);
		inOrder.verify(handler1).on(event1);
	}

	@Test void whenDispatchingEventsItShouldBePassedToRoutedHandlers() {
		val event1     = new Event1();
		val event3     = new Event3();
		val handler1   = mock(Handler1.class);
		val handler4   = mock(Handler4.class);
		val dispatcher = new EventRouter.Builder()
				.route(Event1.class).to(handler1, handler4)
				.route(Event3.class).to(handler4)
			.build();
		dispatcher.dispatch(event1);
		dispatcher.dispatch(event3);
		dispatcher.dispatch(event1);

		val inOrder = inOrder(handler1, handler4);
		inOrder.verify(handler1).on(event1);
		inOrder.verify(handler4).on(event1);
		inOrder.verify(handler4).on(event3);
		inOrder.verify(handler1).on(event1);
		inOrder.verify(handler4).on(event1);
	}

	@Test void whenHandlerImplementsUsesDispatcherItShouldBeSetAsPartOfBuild() {
		val handler5   = mock(Handler5.class);
		val dispatcher = new EventRouter.Builder()
				.route(Event1.class).to(handler5)
				.build();

		verify(handler5).setDispatcher(dispatcher);
	}

	@Test void whenDispatchingEventsItShouldBePassedToRoutedHandlersDepthFirstByDefault() {
		val event1     = new Event1();
		val event2     = new Event2();
		val event3     = new Event3();
		val handler1   = mock(Handler1.class);
		val handler2   = mock(Handler2.class);
		val handler4   = mock(Handler4.class);
		val handler5   = new Handler5();
		val handler6   = new Handler6();
		val dispatcher = new EventRouter.Builder()
				.route(Event1.class).to(handler5, handler1)
				.route(Event2.class).to(handler6, handler2)
				.route(Event3.class).to(handler4)
				.build();
		dispatcher.dispatch(event1);

		val inOrder = inOrder(handler1, handler2, handler4);
		inOrder.verify(handler4).on(event3);
		inOrder.verify(handler2).on(event2);
		inOrder.verify(handler1).on(event1);
	}

	@Test void whenDispatchingEventsItShouldBePassedToRoutedHandlersBreathFirstWhenBreadthFirstUsed() {
		val event1     = new Event1();
		val event2     = new Event2();
		val event3     = new Event3();
		val handler1   = mock(Handler1.class);
		val handler2   = mock(Handler2.class);
		val handler4   = mock(Handler4.class);
		val handler5   = new Handler5();
		val handler6   = new Handler6();
		val dispatcher = new EventRouter.BreadthFirstBuilder()
				.route(Event1.class).to(handler5, handler1)
				.route(Event2.class).to(handler6, handler2)
				.route(Event3.class).to(handler4)
				.build();
		dispatcher.dispatch(event1);

		val inOrder = inOrder(handler1, handler2, handler4);
		inOrder.verify(handler1).on(event1);
		inOrder.verify(handler2).on(event2);
		inOrder.verify(handler4).on(event3);
	}
}
