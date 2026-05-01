package com.devticket.member.common.exception;

public record FieldError(String field, String value, String reason) {

}
