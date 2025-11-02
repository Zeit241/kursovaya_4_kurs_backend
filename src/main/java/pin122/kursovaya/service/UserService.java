package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.Role;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.RoleRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.EncryptPassword;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.HashMap;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<UserDto> createUser(CreateUserDto userDto) {
       Optional<User> user = userRepository.findByEmailOrPhone(userDto.getEmail(), userDto.getPhone());

       if (user.isPresent()) {
           System.out.println("User already exists");
           return Optional.empty();
       }else{
           System.out.println("Creating user");
           User usr = new User();
           String[] fio = userDto.getFio().split("[\\p{Punct}\\s]+");
           usr.setEmail(userDto.getEmail());
           usr.setPhone(userDto.getPhone());
           usr.setFirstName(fio[1]);
           usr.setLastName(fio[0]);
           usr.setMiddleName(fio[2]);
           usr.setPasswordHash(EncryptPassword.hashPassword(userDto.getPassword()));
           Patient patient = new Patient();
           patient.setEmergencyContact(new HashMap<>());
           patient.setUser(usr);
           usr.setPatient(patient);
           // назначаем роль по умолчанию "patient"
           roleRepository.findByCode("patient").ifPresent(role -> usr.getRoles().add(role));
           User createdUsr = userRepository.save(usr);
           return Optional.of(new UserDto(createdUsr));
       }
    }

    public User saveUser(UserDto user) {
        return userRepository.save(new User(user));
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}