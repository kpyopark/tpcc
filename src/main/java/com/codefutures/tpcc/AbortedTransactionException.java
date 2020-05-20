package com.codefutures.tpcc;

public class AbortedTransactionException extends Exception {

    
    /**
     *
     */
    private static final long serialVersionUID = -4101629491558778731L;

    public AbortedTransactionException() {
        super();
    }

    public AbortedTransactionException(String message) {
        super(message);
    }
}
