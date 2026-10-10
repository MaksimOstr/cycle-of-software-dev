package com.cycleofsoftwaredev.ordering.domain;

/** Generates human-readable order numbers shown to customers (for example, SS-261005-00001). */
public interface OrderNumberGenerator {

    String nextNumber();
}
