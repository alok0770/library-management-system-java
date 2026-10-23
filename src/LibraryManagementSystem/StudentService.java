package LibraryManagementSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.InputMismatchException;

import static LibraryManagementSystem.Main.scanner;

public class StudentService {
    ArrayList<Student> students = new ArrayList<>();

    public StudentService() {
        loadStudentsFromDB();
    }

    public void loadStudentsFromDB() {
        students.clear();
        String sql = "SELECT name, branch, year, roll_no, phone_number FROM students";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String name = rs.getString("name");
                String branch = rs.getString("branch");
                int year = rs.getInt("year");
                int rollNo = rs.getInt("roll_no");
                String phone = rs.getString("phone_number");
                students.add(new Student(name, rollNo, branch, year, phone));
            }
        } catch (SQLException e) {
            System.err.println("Failed to load students from database: " + e.getMessage());
        }
    }

    public void addStudent() {
        System.out.print("-> Enter Student name : ");
        String name = scanner.nextLine().toUpperCase().trim();

        System.out.print("-> Enter Branch (e.g. BCA, CSE, AI/ML) : ");
        String branch = scanner.nextLine().toUpperCase().trim();

        int year;
        while (true) {
            year = getValidInt("-> Enter Year (1-4) : ");
            if (year >= 1 && year <= 4) {
                break;
            } else {
                System.out.println("-> Please enter a valid year between 1 and 4!\n");
            }
        }

        int rollNo;
        while (true) {
            rollNo = getValidInt("-> Enter the roll number : ");
            if (isStudentExists(branch, year, rollNo)) {
                System.out.println("-> Student with Roll No " + rollNo + " already exists in " + branch + " Year " + year + "!\n");
            } else {
                break;
            }
        }

        String phoneNumber;
        while (true) {
            System.out.print("-> Enter 10-digit Phone Number : ");
            phoneNumber = scanner.nextLine().trim();
            if (phoneNumber.length() != 10 || !phoneNumber.matches("\\d+")) {
                System.out.println("-> Invalid Phone Number! Please enter exactly 10 digits.\n");
            } else if (isPhoneNumberExists(phoneNumber)) {
                System.out.println("-> Phone number already exists! Please enter a unique phone number.\n");
            } else {
                break;
            }
        }

        StringBuilder error = new StringBuilder();
        if (registerStudent(name, branch, year, rollNo, phoneNumber, error)) {
            System.out.println();
            System.out.println("-> Student saved successfully to database !! \n");
        } else {
            System.out.println("\n-> " + error + "\n");
        }
    }

    public synchronized boolean registerStudent(String name, String branch, int year, int rollNo, String phoneNumber, StringBuilder errorMsg) {
        if (name == null || name.trim().isEmpty()) {
            errorMsg.append("Student name cannot be empty!");
            return false;
        }
        if (branch == null || branch.trim().isEmpty()) {
            errorMsg.append("Branch cannot be empty!");
            return false;
        }
        if (year < 1 || year > 4) {
            errorMsg.append("Year must be between 1 and 4!");
            return false;
        }
        name = name.toUpperCase().trim();
        branch = branch.toUpperCase().trim();
        phoneNumber = phoneNumber == null ? "" : phoneNumber.trim();

        if (phoneNumber.length() != 10 || !phoneNumber.matches("\\d+")) {
            errorMsg.append("Phone number must be exactly 10 digits!");
            return false;
        }

        if (isStudentExists(branch, year, rollNo)) {
            errorMsg.append("Student with Roll No ").append(rollNo).append(" already exists in ").append(branch).append(" Year ").append(year).append("!");
            return false;
        }

        if (isPhoneNumberExists(phoneNumber)) {
            errorMsg.append("Phone number ").append(phoneNumber).append(" already registered to another student!");
            return false;
        }

        String insertSQL = "INSERT INTO students (name, branch, year, roll_no, phone_number) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, name);
            pstmt.setString(2, branch);
            pstmt.setInt(3, year);
            pstmt.setInt(4, rollNo);
            pstmt.setString(5, phoneNumber);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            errorMsg.append("Failed to save student to database: ").append(e.getMessage());
            return false;
        }

        Student newStudent = new Student(name, rollNo, branch, year, phoneNumber);
        students.add(newStudent);
        return true;
    }

    public synchronized boolean removeStudent(String branch, int year, int rollNo, StringBuilder errorMsg) {
        Student s = findStudent(branch, year, rollNo);
        if (s == null) {
            errorMsg.append("Student not found!");
            return false;
        }

        String deleteSQL = "DELETE FROM students WHERE branch = ? AND year = ? AND roll_no = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, branch.toUpperCase().trim());
            pstmt.setInt(2, year);
            pstmt.setInt(3, rollNo);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            errorMsg.append("Failed to delete student from database: ").append(e.getMessage());
            return false;
        }

        students.remove(s);
        return true;
    }

    public ArrayList<Student> getAllStudents() {
        return new ArrayList<>(students);
    }

    public void searchStudent() {
        System.out.print("-> Enter Branch : ");
        String branch = scanner.nextLine().toUpperCase().trim();

        int year = getValidInt("-> Enter Year : ");
        int rollNo = getValidInt("-> Enter Roll Number : ");

        Student student = findStudent(branch, year, rollNo);
        if (student != null) {
            System.out.println(" Name         : " + student.getName()
                    + "\n Branch       : " + student.getBranch()
                    + "\n Year         : " + student.getYear()
                    + "\n Roll No      : " + student.getRollNo()
                    + "\n Phone Number : " + student.getPhoneNumber());
            System.out.println();
        } else {
            System.out.println("\nStudent not exist : \n");
        }
    }

    public void showAll() {
        if (students.isEmpty()) {
            System.out.println("No Students found !! \n");
        } else {
            for (Student student : students) {
                System.out.println(" Name         : " + student.getName()
                        + "\n Branch       : " + student.getBranch()
                        + "\n Year         : " + student.getYear()
                        + "\n Roll No      : " + student.getRollNo()
                        + "\n Phone Number : " + student.getPhoneNumber());
                System.out.println();
            }
            System.out.println();
        }
    }

    public void deleteStudent() {
        System.out.print("-> Enter Branch : ");
        String branch = scanner.nextLine().toUpperCase().trim();

        int year = getValidInt("-> Enter Year : ");
        int rollNo = getValidInt("-> Enter Roll Number : ");

        StringBuilder error = new StringBuilder();
        if (removeStudent(branch, year, rollNo, error)) {
            System.out.println("Student deleted successfully from database !! \n");
        } else {
            System.out.println("-> " + error + " \n");
        }
    }

    public static int getValidInt(String message) {
        int value;
        while (true) {
            try {
                System.out.print(message);
                value = scanner.nextInt();
                scanner.nextLine();
                break;
            } catch (InputMismatchException e) {
                System.out.println("Invalid Input !! ");
                scanner.nextLine();
            }
        }
        return value;
    }

    public boolean isStudentExists(String branch, int year, int rollNo) {
        for (Student s : students) {
            if (s.getBranch().equalsIgnoreCase(branch)
                    && s.getYear() == year
                    && s.getRollNo() == rollNo) {
                return true;
            }
        }
        return false;
    }

    public Student findStudent(String branch, int year, int rollNo) {
        for (Student s : students) {
            if (s.getBranch().equalsIgnoreCase(branch)
                    && s.getYear() == year
                    && s.getRollNo() == rollNo) {
                return s;
            }
        }
        return null;
    }

    public boolean isPhoneNumberExists(String phoneNumber) {
        for (Student s : students) {
            if (s.getPhoneNumber().equals(phoneNumber)) {
                return true;
            }
        }
        return false;
    }
}
