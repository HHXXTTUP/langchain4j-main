package dev.learning.fashionagent.learning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FashionLearningRepository {

    void saveClothingProfile(ClothingProfile profile);

    List<ClothingProfile> listClothingProfiles();

    Optional<ClothingProfile> findClothingProfile(String id);

    void deleteClothingProfilesNotIn(List<String> activeIds);

    void saveExperience(LearnedFashionExperience experience);

    boolean experienceExists(UUID sourceJobId);

    List<LearnedFashionExperience> listApprovedExperiences();
}
