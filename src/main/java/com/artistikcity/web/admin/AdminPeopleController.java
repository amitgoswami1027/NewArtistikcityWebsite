package com.artistikcity.web.admin;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Paginator;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.Storage;
import com.artistikcity.support.Validator;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Teachers, students, student artworks, home page artworks and the admin's own profile
 * (Admin\TeacherController, StudentController, HomeController, ProfileController).
 */
@Controller
public class AdminPeopleController {

    private final Views views;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Validator validator;
    private final Storage storage;
    private final Slugs slugs;
    private final Redirects redirect;

    public AdminPeopleController(Views views, Db db, Json json, Auth auth, Validator validator, Storage storage, Slugs slugs, Redirects redirect) {
        this.views = views;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.validator = validator;
        this.storage = storage;
        this.slugs = slugs;
        this.redirect = redirect;
    }

    private void photo(Input in, String field, String table, String dir, long id, String column) {
        if (in.hasFile(field)) {
            String name = storage.storeUpload(in.file(field), dir + "/" + id);
            db.updateById(table, id, Row.of(column, name));
        }
    }

    // ---------------------------------------------------------------- teachers

    @GetMapping("/admin/teachers")
    public ResponseEntity<String> teachers(HttpServletRequest request) {
        return views.page(request, "admin/teachers/list", Map.of("teachers", db.select("select * from admins where admin_type = 'teacher' order by id")));
    }

    @GetMapping("/admin/teacher/create")
    public ResponseEntity<String> createTeacher(HttpServletRequest request) {
        return views.page(request, "admin/teachers/add", Map.of());
    }

    @PostMapping("/admin/teacher/create")
    public ResponseEntity<String> storeTeacher(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required", "email", "required|email|unique:admins,email", "password", "required|confirmed");
        long id = db.insert("admins", Row.of("name", in.get("name"), "slug", slugs.unique("admins", "slug", in.get("name"), null),
                "email", in.get("email"), "password", auth.hash(in.get("password")), "profile_title", in.get("profile_title"),
                "designation", in.get("designation"), "location", in.get("location"),
                "profile_description", in.get("profile_description"), "admin_type", "teacher"));
        photo(in, "profile_photo", "admins", "uploads/teachers", id, "profile_photo");
        return redirect.routeWith(request, "success", "The teacher has been added!", "admin.teachers");
    }

    @GetMapping("/admin/teacher/edit/{teacher_id}")
    public ResponseEntity<String> editTeacher(HttpServletRequest request, @PathVariable("teacher_id") Long id) {
        Row t = NotFoundException.orFail(db.find("admins", id)).without("password", "remember_token");
        return views.page(request, "admin/teachers/edit", Map.of("teacher", t));
    }

    @PatchMapping("/admin/teacher/edit/{teacher_id}")
    public ResponseEntity<String> updateTeacher(HttpServletRequest request, @PathVariable("teacher_id") Long id) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required", "password", "confirmed");
        NotFoundException.orFail(db.find("admins", id));
        Row v = Row.of("name", in.get("name"), "slug", slugs.unique("admins", "slug", in.get("name"), id),
                "profile_title", in.get("profile_title"), "designation", in.get("designation"), "location", in.get("location"),
                "profile_description", in.get("profile_description"), "admin_type", "teacher");
        if (in.get("password") != null) {
            v.put("password", auth.hash(in.get("password")));
        }
        db.updateById("admins", id, v);
        photo(in, "profile_photo", "admins", "uploads/teachers", id, "profile_photo");
        return redirect.routeWith(request, "success", "The teacher has been updated!", "admin.teachers");
    }

    @GetMapping("/admin/teacher/delete/{teacher_id}")
    public ResponseEntity<String> deleteTeacher(HttpServletRequest request, @PathVariable("teacher_id") Long id) {
        try {
            db.deleteById("admins", id);
            Flash.put(request, "success", "Teacher successfully deleted!");
        } catch (Db.DbException e) {
            Flash.put(request, "error", "This teacher has courses and cannot be deleted.");
        }
        return redirect.route(request, "admin.teachers");
    }

    // ---------------------------------------------------------------- students

    @GetMapping("/admin/students")
    public ResponseEntity<String> students(HttpServletRequest request, @RequestParam(value = "page", required = false) String pageParam) {
        int perPage = 10;
        int page = Paginator.page(pageParam);
        long total = db.count("select count(*) from users");
        List<Row> list = db.select("select * from users order by id offset " + ((page - 1) * perPage) + " rows fetch next " + perPage + " rows only");
        return views.page(request, "admin/students/list", Map.of("students", list,
                "pagination", Paginator.links(total, perPage, page, request.getRequestURI())));
    }

    @GetMapping("/admin/students/create")
    public ResponseEntity<String> createStudent(HttpServletRequest request) {
        return views.page(request, "admin/students/add", Map.of());
    }

    private Row studentFields(Input in) {
        return Row.of("name", in.get("name"), "phone", in.get("phone"),
                "notifications", in.lng("notification") != null ? in.lng("notification") : in.lng("notifications"),
                "language_preference", in.get("language_preference"), "location", in.get("location"),
                "profile_title", in.get("profile_title"), "profile_description", in.get("profile_description"));
    }

    @PostMapping("/admin/students/store")
    public ResponseEntity<String> storeStudent(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required", "email", "required|email|unique:users,email", "password", "required|confirmed");
        Row v = studentFields(in);
        v.put("slug", slugs.unique("users", "slug", in.get("name"), null));
        v.put("email", in.get("email"));
        v.put("password", auth.hash(in.get("password")));
        v.put("status", 1);
        long id = db.insert("users", v);
        photo(in, "profile_photo", "users", "uploads/students", id, "profile_photo");
        return redirect.routeWith(request, "success", "The student has been added!", "admin.students");
    }

    @GetMapping("/admin/students/edit/{student_id}")
    public ResponseEntity<String> editStudent(HttpServletRequest request, @PathVariable("student_id") Long id) {
        Row s = NotFoundException.orFail(db.find("users", id)).without("password", "remember_token");
        s.put("notification", s.get("notifications"));
        return views.page(request, "admin/students/edit", Map.of("student", s));
    }

    @PatchMapping("/admin/students/edit/{student_id}")
    public ResponseEntity<String> updateStudent(HttpServletRequest request, @PathVariable("student_id") Long id) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required", "password", "confirmed");
        NotFoundException.orFail(db.find("users", id));
        Row v = studentFields(in);
        if (in.get("password") != null) {
            v.put("password", auth.hash(in.get("password")));
        }
        db.updateById("users", id, v);
        photo(in, "profile_photo", "users", "uploads/students", id, "profile_photo");
        return redirect.routeWith(request, "success", "The student has been updated!", "admin.students");
    }

    @GetMapping("/admin/students/details/{student_id}")
    public ResponseEntity<String> showStudent(HttpServletRequest request, @PathVariable("student_id") Long id) {
        return redirect.route(request, "admin.students.edit", id);
    }

    @DeleteMapping("/admin/students/delete/{student_id}")
    public ResponseEntity<String> deleteStudent(HttpServletRequest request, @PathVariable("student_id") Long id) {
        try {
            db.update("delete from user_addresses where user_id = ?", id);
            db.update("delete from student_artworks where user_id = ?", id);
            db.deleteById("users", id);
            Flash.put(request, "success", "Student successfully deleted!");
        } catch (Db.DbException e) {
            Flash.put(request, "error", "This student has certificates or task submissions and cannot be deleted.");
        }
        return redirect.route(request, "admin.students");
    }

    // -------------------------------------------------------- student artworks

    @GetMapping("/admin/students/artworks")
    public ResponseEntity<String> studentArtworks(HttpServletRequest request) {
        List<Row> list = db.select("select * from student_artworks order by id");
        for (Row a : list) {
            Row u = db.find("users", a.get("user_id"));
            a.put("user", u == null ? null : u.without("password", "remember_token"));
        }
        return views.page(request, "admin/students/artworks", Map.of("studentArtworks", list));
    }

    @GetMapping("/admin/students/create-artwork")
    public ResponseEntity<String> createStudentArtwork(HttpServletRequest request) {
        return views.page(request, "admin/students/add_artwork", Map.of("students", db.select("select id, name from users order by name")));
    }

    @PostMapping("/admin/students/store-artwork")
    public ResponseEntity<String> storeStudentArtwork(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "photo_name", "required");
        long id = db.insert("student_artworks", Row.of("user_id", in.lng("user_id"), "comments", in.get("comments"),
                "status", in.lng("status") == null ? 1 : in.lng("status")));
        photo(in, "photo_name", "student_artworks", "uploads/artworks", id, "photo_name");
        return redirect.routeWith(request, "success", "The artwork has been added!", "admin.students.artworks");
    }

    @GetMapping("/admin/students/edit-artwork/{artwork_id}")
    public ResponseEntity<String> editStudentArtwork(HttpServletRequest request, @PathVariable("artwork_id") Long id) {
        Row a = NotFoundException.orFail(db.find("student_artworks", id));
        return views.page(request, "admin/students/edit_artwork", Map.of("studentArtwork", a, "students", db.select("select id, name from users order by name")));
    }

    @PatchMapping("/admin/students/edit-artwork/{artwork_id}")
    public ResponseEntity<String> updateStudentArtwork(HttpServletRequest request, @PathVariable("artwork_id") Long id) {
        Input in = Input.of(request, json);
        validator.validate(in, "user_id", "required");
        db.updateById("student_artworks", id, Row.of("user_id", in.lng("user_id"), "comments", in.get("comments"), "status", in.lng("status")));
        photo(in, "photo_name", "student_artworks", "uploads/artworks", id, "photo_name");
        return redirect.routeWith(request, "success", "The artwork has been updated!", "admin.students.artworks");
    }

    @DeleteMapping("/admin/students/delete-artwork/{artwork_id}")
    public ResponseEntity<String> deleteStudentArtwork(HttpServletRequest request, @PathVariable("artwork_id") Long id) {
        db.deleteById("student_artworks", id);
        return redirect.routeWith(request, "success", "Student Artwork successfully deleted!", "admin.students.artworks");
    }

    // ----------------------------------------------------------- home artworks

    @GetMapping("/admin/home/artworks")
    public ResponseEntity<String> homeArtworks(HttpServletRequest request) {
        return views.page(request, "admin/home/artworks", Map.of("homeArtworks", db.select("select * from home_artworks order by id")));
    }

    @GetMapping("/admin/home/create-artwork")
    public ResponseEntity<String> createHomeArtwork(HttpServletRequest request) {
        return views.page(request, "admin/home/add_artwork", Map.of());
    }

    @PostMapping("/admin/home/store-artwork")
    public ResponseEntity<String> storeHomeArtwork(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "photo_name", "required");
        long id = db.insert("home_artworks", Row.of("admin_id", auth.adminId(request), "comments", in.get("comments"),
                "status", in.lng("status") == null ? 1 : in.lng("status")));
        photo(in, "photo_name", "home_artworks", "uploads/home-artworks", id, "photo_name");
        return redirect.routeWith(request, "success", "The artwork has been added!", "admin.home.artworks");
    }

    @GetMapping("/admin/home/edit-artwork/{artwork_id}")
    public ResponseEntity<String> editHomeArtwork(HttpServletRequest request, @PathVariable("artwork_id") Long id) {
        return views.page(request, "admin/home/edit_artwork", Map.of("homeArtwork", NotFoundException.orFail(db.find("home_artworks", id))));
    }

    @PatchMapping("/admin/home/edit-artwork/{artwork_id}")
    public ResponseEntity<String> updateHomeArtwork(HttpServletRequest request, @PathVariable("artwork_id") Long id) {
        Input in = Input.of(request, json);
        validator.validate(in, "comments", "required");
        db.updateById("home_artworks", id, Row.of("admin_id", auth.adminId(request), "comments", in.get("comments"), "status", in.lng("status")));
        photo(in, "photo_name", "home_artworks", "uploads/home-artworks", id, "photo_name");
        return redirect.routeWith(request, "success", "The artwork has been updated!", "admin.home.artworks");
    }

    @DeleteMapping("/admin/home/delete-artwork/{artwork_id}")
    public ResponseEntity<String> deleteHomeArtwork(HttpServletRequest request, @PathVariable("artwork_id") Long id) {
        db.deleteById("home_artworks", id);
        return redirect.routeWith(request, "success", "Artwork successfully deleted!", "admin.home.artworks");
    }

    // ----------------------------------------------------------------- profile

    @GetMapping("/admin/profile")
    public ResponseEntity<String> profile(HttpServletRequest request) {
        return views.page(request, "admin/profile/public_profile", Map.of());
    }

    @PostMapping("/admin/save-profile")
    public ResponseEntity<String> saveProfile(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required");
        long id = auth.adminId(request);
        db.updateById("admins", id, Row.of("name", in.get("name"), "profile_title", in.get("profile_title"),
                "designation", in.get("designation"), "location", in.get("location"), "profile_description", in.get("profile_description")));
        photo(in, "profile_photo", "admins", "uploads/teachers", id, "profile_photo");
        auth.refresh(request);
        return redirect.routeWith(request, "success", "The profile has been updated!", "admin.public.profile");
    }

    @GetMapping("/admin/change-password")
    public ResponseEntity<String> changePassword(HttpServletRequest request) {
        return views.page(request, "admin/profile/change_password", Map.of());
    }

    @PostMapping("/admin/update-password")
    public ResponseEntity<String> updatePassword(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "old_password", "required", "new_password", "required|confirmed");
        Row hash = db.first("select password from admins where id = ?", auth.adminId(request));
        if (!auth.check(in.get("old_password"), hash.str("password"))) {
            return redirect.backWith(request, "error", "Old Password Doesn't match!");
        }
        db.updateById("admins", auth.adminId(request), Row.of("password", auth.hash(in.get("new_password"))));
        return redirect.backWith(request, "success", "Password changed successfully!");
    }
}
