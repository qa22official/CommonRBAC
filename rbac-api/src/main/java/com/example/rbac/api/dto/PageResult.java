package com.example.rbac.api.dto;

import java.util.List;

public record PageResult<T>(List<T> records, long total, int page, int size) {

    public PageResult {
        records = List.copyOf(records);
    }
}
