package eu.woolplatform.webservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import eu.woolplatform.webservice.controller.ErrorController;

/**
 * This exception results in a HTTP response with status 501 Not Implemented.
 * The exception message (default "Not Implemented") will be written to the
 * response. It is handled by the {@link ErrorController ErrorController}.
 * 
 * @author Dennis Hofs (RRD)
 */
@ResponseStatus(value=HttpStatus.NOT_IMPLEMENTED)
public class NotImplementedException extends HttpException {
	private static final long serialVersionUID = 1L;

	public NotImplementedException() {
		super("Not Implemented");
	}

	public NotImplementedException(String message) {
		super(message);
	}
	
	public NotImplementedException(String code, String message) {
		super(code, message);
	}
	
	public NotImplementedException(HttpError error) {
		super(error);
	}
}
