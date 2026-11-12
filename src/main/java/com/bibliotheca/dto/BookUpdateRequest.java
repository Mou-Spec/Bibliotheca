package com.bibliotheca.dto;

public record BookUpdateRequest(String title, String author, String tags, String description) {}
