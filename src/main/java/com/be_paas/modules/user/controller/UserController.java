package com.be_paas.modules.user.controller;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.user.dto.AddNewUser;
import com.be_paas.modules.user.dto.UserResponse;
import com.be_paas.modules.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"", "/"})
    public PageResponse<UserResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return userService.findAll(search, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody AddNewUser createRequest){
        return userService.create(createRequest);
    }
}
