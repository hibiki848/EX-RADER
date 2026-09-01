package com.exradar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  String notFound(ResourceNotFoundException e, Model model) {
    model.addAttribute("message", e.getMessage());
    return "error/404";
  }

  @ExceptionHandler(ForbiddenOperationException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  String forbidden(ForbiddenOperationException e, Model model) {
    model.addAttribute("message", e.getMessage());
    return "error/403";
  }
}
