package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.UpdateUserLocationRequest;
import com.oneforall.padobackend.dto.UserLocationResponse;
import com.oneforall.padobackend.entity.LocationSource;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserLocationService {
    private final UserRepository users;

    public UserLocationService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserLocationResponse findCurrent(String email) {
        return UserLocationResponse.from(requireUser(email));
    }

    @Transactional
    public UserLocationResponse update(String email, UpdateUserLocationRequest request) {
        User user = requireUser(email);
        String label = request.label().trim();
        if (request.source() == LocationSource.GPS) {
            if (request.latitude() == null || request.longitude() == null || request.accuracy() == null) {
                throw new BadRequestException("GPS location requires latitude, longitude and accuracy");
            }
            if (!Double.isFinite(request.latitude()) || !Double.isFinite(request.longitude())
                    || !Double.isFinite(request.accuracy())) {
                throw new BadRequestException("Location values must be finite numbers");
            }
            user.setLocationLatitude(request.latitude());
            user.setLocationLongitude(request.longitude());
            user.setLocationAccuracy(request.accuracy());
        } else {
            user.setLocationLatitude(null);
            user.setLocationLongitude(null);
            user.setLocationAccuracy(null);
        }
        user.setLocationLabel(label);
        user.setLocationSource(request.source());
        user.setLocationUpdatedAt(Instant.now());
        return UserLocationResponse.from(users.save(user));
    }

    @Transactional
    public UserLocationResponse clear(String email) {
        User user = requireUser(email);
        user.setLocationLabel(null);
        user.setLocationLatitude(null);
        user.setLocationLongitude(null);
        user.setLocationAccuracy(null);
        user.setLocationSource(null);
        user.setLocationUpdatedAt(null);
        return UserLocationResponse.from(users.save(user));
    }

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
