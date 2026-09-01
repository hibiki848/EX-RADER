package com.exradar.dto;

public record SimilarityDto(
    Long id, String title, String displayName, int score, String explanation, boolean user) {}
