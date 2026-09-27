package com.artistikcity.web.admin;

import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin dashboard and the student task review list (Admin\DashboardController, Admin\TaskController). */
@Controller
public class AdminDashboardController {

    private final Views views;
    private final Db db;

    public AdminDashboardController(Views views, Db db) {
        this.views = views;
        this.db = db;
    }

    @GetMapping("/admin/dashboard/classic")
    public ResponseEntity<String> dashboard(HttpServletRequest request) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usersCount", db.count("select count(*) from users"));
        m.put("coursesCount", db.count("select count(*) from courses"));
        m.put("bookedCourseCount", db.count("select count(*) from orders where payment_status = 1"));
        m.put("totalRevenueINR", sum("inr"));
        m.put("totalRevenueUSD", sum("usd"));
        m.put("bookedCourses", db.select("select top 5 c.title, c.course_start_date, o.id, o.order_number, o.created_at, o.price, o.price_type,"
                + " c.id as course_id, u.id as user_id, u.name, u.email, " + CourseService.photoSubquery("o.course_id")
                + " from orders o join courses c on c.id = o.course_id join users u on u.id = o.user_id"
                + " where o.payment_status = 1 and o.payment_id <> '' order by o.id desc"));
        m.put("freeCourseSubscription", db.select("select top 5 * from users where status = 1 and free_courses = 1 order by id desc"));
        m.put("getMessages", taskList());
        return views.page(request, "admin/dashboard", m);
    }

    private double sum(String type) {
        double total = 0;
        for (Row r : db.select("select price from orders where payment_status = 1 and price_type = ?", type)) {
            try {
                total += Double.parseDouble(r.str("price"));
            } catch (NumberFormatException | NullPointerException ignored) {
                // non numeric price
            }
        }
        return total;
    }

    /** Latest student submissions (from_type = Student). */
    List<Row> taskList() {
        return db.select("select top 10 st.id, st.task_id, st.student_teacher_connect_id, st.reply, st.review_status, st.created_at,"
                + " u.id as user_id, u.name, u.email, u.profile_photo from student_tasks st join users u on u.id = st.from_id"
                + " where st.from_type = 'Student' order by st.id desc");
    }

    @GetMapping("/admin/tasks")
    public ResponseEntity<String> tasks(HttpServletRequest request) {
        return views.page(request, "admin/tasks/tasks", Map.of("getMessages", taskList()));
    }
}
