# Event Router

## Introduction

This library implements a low latency router for events. It's similar to Guava's EventBus, but the routing is explicitly
specified using a DSL to provide an overview. Furthermore, the implementation is ~6 times faster than Guava's EventBus.

The library can be used for efficiently dispatching different types of events to different handlers. In Danske Bank, we 
typically use this library to read events from different sources, publish them to an LMAX Disruptor and then route the
events to an appropriate handler on the Disruptor thread.

## Dispatching

A key interface is the `Dispatcher` interface:

    public interface Dispatcher {
        void dispatch(Object event);
    }

## Event Routing

The routing of events to handlers can be specified via a DSL. Under the hood, it creates a custom dispatcher.

As an example:

    Dispatcher router = new EventRouter.Builder()
        .route(     Leader.class).to(priceHandler, tradeHandler)
        .route(  NotLeader.class).to(tradeHandler, priceHandler)
        .route(   NewTrade.class).to(tradeHandler)
        .route(   NewPrice.class).to(priceHandler)
        .route(       Tick.class).to(priceHandler)
        .route(ClearPrices.class).to(priceHandler)
    .build();

A handler class must have a single, public method taking the event class (or one of its interfaces) as the only argument.
The name of the method is not important and can be chosen depending on readability and style. E.g. the `TradeHandler` could
look like:

    public class TradeHandler {
        public void on(Leader leader)       { /* Implementation...*/ }
        public void on(NotLeader notLeader) { /* Implementation...*/ }
        public void on(NewTrade newTrade)   { /* Implementation...*/ }
    }

The order that handlers are listed in is guaranteed to be the order that events will be dispatched in.

The default is to process events depth-first. I.e. if a handler dispatches a new event back to the EventRouter, it will
be dispatched immediately to the next handler.

Events are routed (dispatched) to its handler(s) by invoking:

    router.dispatch(event);

If breadth-first semantics are desired, there's an alternative builder that can be used:

    Dispatcher router = new EventRouter.BreadthFirstBuilder()
        .route(     Leader.class).to(priceHandler, tradeHandler)
        .route(  NotLeader.class).to(tradeHandler, priceHandler)
        .route(   NewTrade.class).to(tradeHandler)
        .route(   NewPrice.class).to(priceHandler)
        .route(       Tick.class).to(priceHandler)
        .route(ClearPrices.class).to(priceHandler)
    .build();

Now if e.g. an `Leader` event is dispatched, it will be forwarded to the `priceHandler` and then the `tradeHandler` even
if the `priceHandler` dispatched other events as part of its processing of the `Leader` event.

### Handlers Using The Dispatcher for Output Events

Often handlers also need to dispatch new events as a result of handling input events.
Because the handlers need to be created before setting up the routing of events, it's not possible to pass an
`EventRouter` (`Dispatcher`) instance in the constructor, and it has to be set after the `EventRouter` is built:

    handler.setDispacher(eventRouter);

This can be cumbersome and easy to forget. Therefore, handlers implementing the `UsesDispatcher` interface will
automatically have the `setDispacher` method invoked when the `EventRouter` is built. As an example:

    class MyHandler implements UsesDispatcher {
        @Override public void setDispatcher(Dispatcher dispatcher) {
            // Store it in a field, whatever.
        }
    };

    // ...

    var myHandler         = new MyHandler();
    Dispatcher dispatcher = new EventRouter.Builder()
            .route(Leader.class).to(myHandler)
        .build(); // EventRouter is set in the myHandler here (under the hood).

### Logging

The default logging level is INFO.

However, all events can be logged at another level by passing the appropriate level in the `Builder` constructor, and
it can even be overridden on an event level basis:

    var dispatcher = new EventRouter.Builder(DEBUG)
        .route(     Leader.class).to(priceHandler, tradeHandler)
        .route(  NotLeader.class).to(tradeHandler).logAs(WARN)
        .route(   NewTrade.class).to(tradeHandler)
        .route(   NewPrice.class).to(priceHandler)
        .route(       Tick.class).to(priceHandler).logAs(TRACE)
        .route(ClearPrices.class).to(priceHandler)
    .build();

In the above example, all events are logged as `DEBUG` except `NotLeader` which is logged at `WARN` and `Tick` which
is logged at `TRACE` level.