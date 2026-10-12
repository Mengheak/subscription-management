package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.dto.auth.AuthResponse;
import com.chheang.mengheak.subscriptionmanager.dto.auth.LoginRequest;
import com.chheang.mengheak.subscriptionmanager.dto.auth.RegisterTenantRequest;

public interface AuthService {

    AuthResponse register(RegisterTenantRequest request);

    AuthResponse login(LoginRequest request);
}
