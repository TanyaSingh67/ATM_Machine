import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * ATM Machine - a menu-driven banking program built with Java and OOP.
 *
 * Features: PIN authentication (with account lock), balance inquiry,
 * cash withdrawal, deposit, mini statement, change PIN, exception handling.
 *
 * Compile: javac ATM.java
 * Run:     java ATM
 *
 * Demo accounts: 1001 / PIN 1234   and   1002 / PIN 4321
 */

// ---------- Custom exceptions ----------
class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) { super(message); }
}

class InvalidAmountException extends Exception {
    public InvalidAmountException(String message) { super(message); }
}

class AccountLockedException extends Exception {
    public AccountLockedException(String message) { super(message); }
}

// ---------- Transaction record ----------
class Transaction {
    private final String type;
    private final double amount;
    private final double balanceAfter;
    private final long time = System.currentTimeMillis();

    public Transaction(String type, double amount, double balanceAfter) {
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public String getType() { return type; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public long getTime() { return time; }

    @Override
    public String toString() {
        return String.format("%-12s Rs. %10.2f   Balance: Rs. %10.2f", type, amount, balanceAfter);
    }
}

// ---------- Account (encapsulation) ----------
class Account {
    private static final int MAX_PIN_ATTEMPTS = 3;
    private static final double WITHDRAW_LIMIT = 20000;

    private final String accountNumber;
    private final String holderName;
    private int pin;
    private double balance;
    private int failedAttempts = 0;
    private boolean locked = false;
    private final List<Transaction> history = new ArrayList<>();

    public Account(String accountNumber, String holderName, int pin, double openingBalance) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = openingBalance;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }
    public double getBalance() { return balance; }
    public boolean isLocked() { return locked; }

    /** Validates the PIN; locks the account after too many wrong attempts. */
    public boolean authenticate(int enteredPin) throws AccountLockedException {
        if (locked) {
            throw new AccountLockedException("Account is locked. Please contact your bank.");
        }
        if (enteredPin == pin) {
            failedAttempts = 0;
            return true;
        }
        failedAttempts++;
        if (failedAttempts >= MAX_PIN_ATTEMPTS) {
            locked = true;
            throw new AccountLockedException("Too many wrong attempts. Account locked.");
        }
        return false;
    }

    public int attemptsLeft() { return MAX_PIN_ATTEMPTS - failedAttempts; }

    public void deposit(double amount) throws InvalidAmountException {
        if (amount <= 0) {
            throw new InvalidAmountException("Deposit amount must be greater than zero.");
        }
        balance += amount;
        history.add(new Transaction("DEPOSIT", amount, balance));
    }

    public void withdraw(double amount) throws InvalidAmountException, InsufficientFundsException {
        if (amount <= 0) {
            throw new InvalidAmountException("Withdrawal amount must be greater than zero.");
        }
        if (amount % 100 != 0) {
            throw new InvalidAmountException("Amount must be a multiple of 100.");
        }
        if (amount > WITHDRAW_LIMIT) {
            throw new InvalidAmountException("Per-transaction limit is Rs. " + (int) WITHDRAW_LIMIT + ".");
        }
        if (amount > balance) {
            throw new InsufficientFundsException("Insufficient balance. Available: Rs. " + balance);
        }
        balance -= amount;
        history.add(new Transaction("WITHDRAWAL", amount, balance));
    }

    public boolean changePin(int oldPin, int newPin) {
        if (oldPin != pin || newPin < 1000 || newPin > 9999) {
            return false;
        }
        pin = newPin;
        return true;
    }

    /** Returns up to the last 5 transactions. */
    public List<Transaction> getMiniStatement() {
        int from = Math.max(0, history.size() - 5);
        return history.subList(from, history.size());
    }
}

// ---------- Bank (holds accounts) ----------
class Bank {
    private final Map<String, Account> accounts = new HashMap<>();

    public void addAccount(Account account) {
        accounts.put(account.getAccountNumber(), account);
    }

    public Account findAccount(String accountNumber) {
        return accounts.get(accountNumber);
    }
}

// ---------- ATM (user interface + workflow) ----------
public class ATM {
    private final Bank bank;
    private final Scanner sc = new Scanner(System.in);

    public ATM(Bank bank) { this.bank = bank; }

    public void start() {
        System.out.println("=====================================");
        System.out.println("        WELCOME TO JAVA ATM");
        System.out.println("=====================================");

        while (true) {
            System.out.print("\nEnter account number (or 0 to exit): ");
            String accNo = sc.nextLine().trim();
            if (accNo.equals("0")) {
                System.out.println("Thank you for using the ATM. Goodbye!");
                break;
            }

            Account account = bank.findAccount(accNo);
            if (account == null) {
                System.out.println("Account not found.");
                continue;
            }
            if (login(account)) {
                showMenu(account);
            }
        }
    }

    private boolean login(Account account) {
        while (true) {
            try {
                int pin = readInt("Enter 4-digit PIN: ");
                if (account.authenticate(pin)) {
                    System.out.println("\nLogin successful. Welcome, " + account.getHolderName() + "!");
                    return true;
                }
                System.out.println("Incorrect PIN. Attempts left: " + account.attemptsLeft());
            } catch (AccountLockedException e) {
                System.out.println(e.getMessage());
                return false;
            }
        }
    }

    private void showMenu(Account account) {
        boolean running = true;
        while (running) {
            System.out.println("\n------------- ATM MENU -------------");
            System.out.println("1. Balance Inquiry");
            System.out.println("2. Cash Withdrawal");
            System.out.println("3. Deposit");
            System.out.println("4. Mini Statement");
            System.out.println("5. Change PIN");
            System.out.println("6. Logout");
            int choice = readInt("Choose an option: ");

            try {
                switch (choice) {
                    case 1:
                        System.out.printf("Available balance: Rs. %.2f%n", account.getBalance());
                        break;
                    case 2:
                        account.withdraw(readDouble("Enter amount to withdraw: "));
                        System.out.printf("Please collect your cash. New balance: Rs. %.2f%n", account.getBalance());
                        break;
                    case 3:
                        account.deposit(readDouble("Enter amount to deposit: "));
                        System.out.printf("Deposit successful. New balance: Rs. %.2f%n", account.getBalance());
                        break;
                    case 4:
                        printStatement(account);
                        break;
                    case 5:
                        changePin(account);
                        break;
                    case 6:
                        System.out.println("Logged out. Please take your card.");
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid option. Try again.");
                }
            } catch (InvalidAmountException | InsufficientFundsException e) {
                System.out.println("Transaction failed: " + e.getMessage());
            }
        }
    }

    private void printStatement(Account account) {
        List<Transaction> list = account.getMiniStatement();
        if (list.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        System.out.println("---- Last transactions ----");
        for (Transaction t : list) {
            System.out.println(t);
        }
    }

    private void changePin(Account account) {
        int oldPin = readInt("Enter current PIN: ");
        int newPin = readInt("Enter new 4-digit PIN: ");
        if (account.changePin(oldPin, newPin)) {
            System.out.println("PIN changed successfully.");
        } else {
            System.out.println("PIN change failed. Check your current PIN and use exactly 4 digits.");
        }
    }

    // ---------- Safe input helpers (exception handling) ----------
    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid amount.");
            }
        }
    }

    public static void main(String[] args) {
        Bank bank = new Bank();
        bank.addAccount(new Account("1001", "Taa", 1234, 15000));
        bank.addAccount(new Account("1002", "Demo User", 4321, 5000));
        new ATM(bank).start();
    }
}
