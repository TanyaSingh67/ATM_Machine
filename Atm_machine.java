import java.util.Scanner;
 class Atm{
    float balance;
    int PIN =5678;
    public void checkpin(){
        System.err.println("Enter your pin:");
        Scanner sc =new Scanner(System.in);
int enteredpin=sc.nextInt();
if(enteredpin==PIN){
    menu();
}else{
    System.err.println("Enter a valid PIN");
}
    }

    /**
     * 
     */
    public void menu(){
    System.err.println("Enter Your Choice:");
    System.err.println("1. Check Account Balance");
    System.err.println("2. Withdraw Money");
    System.err.println("3. Deposite Money");
    System.err.println("4. EXIT");

    Scanner sc=new Scanner(System.in);
    int opt=sc.nextInt();
    if(opt==1){
        checkBalance();
    }else if(opt==2){
       withdrawMoney();
    }else if (opt==3){
        depositeMoney();
    }else if(opt==4){
       
    }else{
        System.err.println("Enter a Valid Choice");
    }
    }

    private void checkBalance() {
        System.out.println("Balance: "+ balance);
        menu();
    }

    private void depositeMoney() {
        System.out.println("Enter the Amount: ");
        Scanner sc=new Scanner(System.in);
        float amount=sc.nextFloat();
        balance=balance+amount;
        System.err.println("Money Deposited Successfully");
    }

    private void withdrawMoney() {
        System.out.println("Enter Amount to Withdraw:");
        Scanner sc=new Scanner(System.in);
        float amount=sc.nextFloat();
        if(balance>=amount){
            System.out.println("Insufficient Balance");
        }else{
            balance=balance-amount;
            System.out.println("Money Withdrawal Successful");
        }
        menu();
    }
}

public class Atm_machine{
    public static void main(String[] args) {
        
    }
}
