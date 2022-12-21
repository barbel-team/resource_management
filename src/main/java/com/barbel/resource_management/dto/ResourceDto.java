package com.barbel.resource_management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDto {
    String name;
    boolean cpuP;
    boolean memoryP;
}
