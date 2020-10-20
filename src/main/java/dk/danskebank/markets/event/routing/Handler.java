package dk.danskebank.markets.event.routing;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.event.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Slf4j
class Handler {
	private final Object target;
	private final Method method;
	private final Level level;
	private final String logString;

	public Handler(Object target, Method method, Level level) {
		this.target         = target;
		this.method         = method;
		this.level          = level;
		val targetClassName = target.getClass().getSimpleName();
		this.logString      = "Dispatching to "+targetClassName+": {}.";
	}

	public void dispatch(Object event) {
		try {
			logTheDispatch(event);
			// Performance critical.
			method.invoke(target, event);
		}
		catch (IllegalAccessException | InvocationTargetException e) {
			throw new Error("Exception occurred while dispatching "+event+" to "+ target, e);
		}
	}

	private void logTheDispatch(Object event) {
		switch (level) {
			case TRACE: log.trace(logString, event); break;
			case DEBUG: log.debug(logString, event); break;
			case INFO:  log.info( logString, event); break;
			case WARN:  log.warn( logString, event); break;
			case ERROR: log.error(logString, event); break;
			default: throw new IllegalStateException("Unknown log level: "+level);
		}
	}
}
