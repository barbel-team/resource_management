package com.barbel.resource_management.Controller;

import com.barbel.resource_management.dto.ResourceDto;
import com.barbel.resource_management.service.ResourceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/resource")
public class ResouceController {
    ResourceService resourceService;
    List<ResourceDto> list;

    @Autowired
    public ResouceController(ResourceService resourceService)
    {
        this.resourceService = resourceService;
        list = new ArrayList<>();
    }

    @GetMapping("/monitor")
    public String monitoring() throws IOException, InterruptedException
    {
        String result = resourceService.MonitorAll();

        return result;
    }

    @GetMapping("/check")
    public void check() throws IOException
    {
        list = resourceService.check();
    }
}
