package LibraryManagementSystem;

import java.util.InputMismatchException;
import java.util.Scanner;

public class Main {
    static Scanner scanner = new Scanner(System.in);
    static StudentService studentService;
    static BookService bookService;

    public static void main(String[] args) {
        // Initialize SQLite Database and create tables if not exist
        DatabaseHelper.initializeDatabase();

        studentService = new StudentService();
        bookService = new BookService(studentService);

        // Start background HTTP Web Server on port 8081
        try {
            LibraryHttpServer httpServer = new LibraryHttpServer(8081, studentService, bookService);
            httpServer.start();
        } catch (Exception e) {
            System.err.println("Note: Web Server could not bind to port 8081 (" + e.getMessage() + "). Console mode is fully active.");
        }

        System.out.println("LIBRARY MANAGEMENT SYSTEM ");
        System.out.println("==========================");
        System.out.println();

        while (true) {
            Main.menu();

            System.out.println("-> Hey welcome :) ");

            int choice = 0;
            boolean valid = false;

            while (!valid) {
                try {
                    System.out.print("\n-> Enter your choice : ");
                    choice = scanner.nextInt();
                    scanner.nextLine();
                    valid = true;

                } catch (InputMismatchException e) {
                    scanner.nextLine();
                    System.out.println("-> Invalid choice : ");
                }
            }

            if (choice == 13) {
                System.out.println("Thanks for using :) ");
                break;
            }

            switch (choice) {
                case 1 -> {
                    System.out.println("Adding Book :");
                    System.out.println("-------------\n");
                    bookService.addBook();
                }

                case 2 -> {
                    System.out.println("Showing All Books :");
                    System.out.println("-------------------\n");
                    bookService.showAll();
                }

                case 3 -> {
                    System.out.println("Searching Book :");
                    System.out.println("----------------\n");
                    bookService.searchBook();
                }

                case 4 -> {
                    System.out.println("Deleting Book :");
                    System.out.println("---------------\n");
                    bookService.deleteBook();
                }

                case 5 -> {
                    System.out.println("Adding Student :");
                    System.out.println("----------------\n");
                    studentService.addStudent();
                }

                case 6 -> {
                    System.out.println("Showing All Students :");
                    System.out.println("----------------------\n");
                    studentService.showAll();
                }

                case 7 -> {
                    System.out.println("Searching Student :");
                    System.out.println("-------------------\n");
                    studentService.searchStudent();
                }

                case 8 -> {
                    System.out.println("Deleting Student :");
                    System.out.println("------------------\n");
                    studentService.deleteStudent();
                }

                case 9 -> {
                    System.out.println("Issuing Book :");
                    System.out.println("--------------\n");
                    bookService.issueBook(studentService);
                }

                case 10 -> {
                    System.out.println("Returning Book :");
                    System.out.println("----------------\n");
                    bookService.returnBook();
                }

                case 11 -> {
                    bookService.showIssuedBooks();
                }

                case 12 -> {
                    bookService.showIssueHistory();
                }

                default -> System.out.println("Invalid choice :\n");
            }
        }
    }

    public static void menu() {
        System.out.println("======================================");
        System.out.println("             Main Menu                ");
        System.out.println("======================================");
        System.out.println();
        System.out.println("--- BOOK MANAGEMENT ---");
        System.out.println("1. Add Book ");
        System.out.println("2. Show All Books ");
        System.out.println("3. Search Book ");
        System.out.println("4. Delete Book ");
        System.out.println();
        System.out.println("--- STUDENT MANAGEMENT ---");
        System.out.println("5. Add Student ");
        System.out.println("6. Show All Students ");
        System.out.println("7. Search Student ");
        System.out.println("8. Delete Student ");
        System.out.println();
        System.out.println("--- ISSUE & RETURN ---");
        System.out.println("9. Issue Book ");
        System.out.println("10. Return Book ");
        System.out.println("11. Show All Issued Books ");
        System.out.println("12. Show Issue & Return History ");
        System.out.println();
        System.out.println("13. Exit :) ");
        System.out.println();
    }
}


