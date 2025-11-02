package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;

@Service
public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User registerUser(User user) {
        // TODO: hash password, validate uniqueness
        return userRepository.save(user);
    }

    public User authenticate(String email, String password) {
        // TODO: verify password hash
        return userRepository.findByEmail(email);
    }
}