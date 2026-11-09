package com.artistikcity.tools;

import com.artistikcity.auth.PasswordPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Prints the bcrypt hash of a password read from standard input (never from the command line, so it doesn't end up in
 * the shell history or the process list). Used by GO-LIVE-RESET to create the first accounts. Checks the same rules
 * as the site ({@link PasswordPolicy}); {@code --staff} also asks for 12+ characters. Exit 2 = refused (reason on stderr).
 * <pre>java -cp app.jar -Dloader.main=com.artistikcity.tools.HashPassword org.springframework.boot.loader.launch.PropertiesLauncher [--staff] email</pre>
 */
public final class HashPassword {

    private HashPassword() {
    }

    public static void main(String[] args) throws Exception {
        boolean staff = false;
        String email = null;
        for (String a : args) {
            if ("--staff".equals(a)) staff = true;
            else email = a;
        }
        String password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        String problem = check(password, email, staff);
        if (problem != null) {
            System.err.println(problem);
            System.exit(2);
        }
        System.out.println(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 12).encode(password));
    }

    static String check(String password, String email, boolean staff) {
        if (password == null || password.isEmpty()) return "No password was given.";
        if (staff && password.length() < 12) return "Use at least 12 characters for the super admin.";
        return PasswordPolicy.problem(password, email, null);
    }
}
