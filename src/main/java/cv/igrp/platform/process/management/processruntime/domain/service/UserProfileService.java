package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processruntime.domain.models.UserProfile;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserProfileService {

  private final UserProfileRepository userProfileRepository;

  public UserProfileService(UserProfileRepository iamUserProfileRepository) {
    this.userProfileRepository = iamUserProfileRepository;
  }

  public UserProfile createUserProfile(UserProfile userProfile) {
    Optional<UserProfile> optionalIAMUserProfile= userProfileRepository.findBySubjectOrEmail(
        userProfile.getSub(),
        userProfile.getEmail()
    );
    return optionalIAMUserProfile.orElseGet(() -> userProfileRepository.save(userProfile));
  }

}
