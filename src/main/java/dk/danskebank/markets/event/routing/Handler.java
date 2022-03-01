package dk.danskebank.markets.event.routing;

import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.logging.log4j.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.apache.logging.log4j.Level.INFO;

@Log4j2
class Handler {
	private final Object target;
	private final Method method;
	private final Level level;
	private final String logString;

	Handler(Object target, Method method, Level level) {
		this.target         = target;
		this.method         = method;
		this.level          = level;
		val targetClassName = target.getClass().getSimpleName();
		this.logString      = "Dispatching to "+targetClassName+": {}.";
	}

	void dispatch(Object event) {
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
		if (Level.TRACE.equals(level)) {
			log.trace(logString, event);
		} else if (Level.DEBUG.equals(level)) {
			log.debug(logString, event);
		} else if (INFO.equals(level)) {
			log.info(logString, event);
		} else if (Level.WARN.equals(level)) {
			log.warn(logString, event);
		} else if (Level.ERROR.equals(level)) {
			log.error(logString, event);
		} else {
			throw new IllegalStateException("Unknown log level: " + level);
		}
	}
}
