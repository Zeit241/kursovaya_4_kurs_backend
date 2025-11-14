package pin122.kursovaya.controller;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.dto.validation.OnCreate;
import pin122.kursovaya.model.User;
import pin122.kursovaya.service.UserService;

import java.util.List;


@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"", "/"})
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/create")
    public ResponseEntity<UserDto> createUser(@Validated @RequestBody CreateUserDto user, BindingResult result) {
        if(result.hasErrors()){
            System.out.println(result.getAllErrors());
            return ResponseEntity.badRequest().body(null);
        }
        return userService.createUser(user).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Validated(OnCreate.class) @RequestBody UserDto userDetails) {
        userService.getUserById(id).orElseThrow(()->new EntityNotFoundException("User not found"));
        User savedUser = userService.saveUser(userDetails);
        return ResponseEntity.ok(new UserDto(savedUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}