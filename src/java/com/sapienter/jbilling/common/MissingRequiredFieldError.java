package com.sapienter.jbilling.common;

public class MissingRequiredFieldError extends SessionInternalError {

	public MissingRequiredFieldError() {
		// TODO Auto-generated constructor stub
	}

	public MissingRequiredFieldError(String s) {
		super(s);
		// TODO Auto-generated constructor stub
	}

	public MissingRequiredFieldError(String s, Class className, Exception e) {
		super(s, className, e);
		// TODO Auto-generated constructor stub
	}

	public MissingRequiredFieldError(Exception e) {
		super(e);
		// TODO Auto-generated constructor stub
	}

	public MissingRequiredFieldError(String message, Throwable e) {
		super(message, e);
		// TODO Auto-generated constructor stub
	}

	public MissingRequiredFieldError(String message, Throwable e,
			String[] errors) {
		super(message, e, errors);
		// TODO Auto-generated constructor stub
	}

	public MissingRequiredFieldError(String message, String[] errors) {
		super(message, errors);
		// TODO Auto-generated constructor stub
	}

}
