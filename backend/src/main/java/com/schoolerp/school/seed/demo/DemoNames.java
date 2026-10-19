package com.schoolerp.school.seed.demo;

import java.util.List;
import java.util.Random;

/**
 * Common first names and surnames, combined at random. Every person in the
 * demo data is made up; phone numbers use the 900xxxxxxx range and Aadhaar is
 * never filled in.
 */
final class DemoNames {

	private DemoNames() {
	}

	static final List<String> BOYS = List.of("Aarav", "Abhiram", "Aditya", "Akhil", "Anirudh", "Arjun", "Bharath",
			"Charan", "Chaitanya", "Dhanush", "Eshwar", "Ganesh", "Harsha", "Hemanth", "Jaswanth", "Karthik", "Kiran",
			"Lokesh", "Madhav", "Manikanta", "Mohan", "Naveen", "Nikhil", "Pavan", "Pranav", "Rahul", "Rakesh", "Ravi",
			"Rohith", "Sai Kiran", "Sai Teja", "Sandeep", "Shiva", "Srikanth", "Sriram", "Sumanth", "Surya", "Tarun",
			"Teja", "Uday", "Varun", "Venkat", "Vamsi", "Vijay", "Vikram", "Vinay", "Vishnu", "Yashwanth", "Ayaan",
			"Imran", "Sameer", "Joseph", "Daniel");

	static final List<String> GIRLS = List.of("Aaradhya", "Akshaya", "Ananya", "Anusha", "Bhavya", "Chandana",
			"Deepika", "Divya", "Harika", "Harini", "Hasini", "Keerthana", "Kavya", "Lahari", "Lasya", "Madhuri",
			"Manasa", "Meghana", "Mounika", "Navya", "Nandini", "Niharika", "Pallavi", "Pooja", "Pranavi", "Priya",
			"Rishitha", "Sahithi", "Samhitha", "Sanjana", "Sindhu", "Sneha", "Sravani", "Sreeja", "Sushma", "Swathi",
			"Tejaswini", "Varsha", "Vaishnavi", "Varshini", "Vennela", "Yamini", "Ayesha", "Fatima", "Sana", "Mary",
			"Grace");

	static final List<String> MEN = List.of("Srinivas", "Venkatesh", "Ramesh", "Suresh", "Mahesh", "Rajesh",
			"Prakash", "Narasimha", "Satyanarayana", "Ravi Kumar", "Anil", "Sunil", "Krishna", "Murali", "Nagaraju",
			"Prasad", "Raghavendra", "Sudhakar", "Chandrasekhar", "Madhusudhan", "Kishore", "Sridhar", "Bhaskar",
			"Gopal", "Rafiq", "Saleem", "John", "Paul");

	static final List<String> WOMEN = List.of("Lakshmi", "Padma", "Swapna", "Sujatha", "Kalpana", "Radha",
			"Jyothi", "Anitha", "Sunitha", "Vijaya", "Sarala", "Madhavi", "Rajeswari", "Sailaja", "Aruna", "Hymavathi",
			"Kavitha", "Rani", "Shobha", "Sireesha", "Nasreen", "Shabana", "Mercy", "Ruth");

	/** Common surnames. No caste is recorded for any demo student. */
	static final List<String> SURNAMES = List.of("Reddy", "Rao", "Naidu", "Goud", "Sharma", "Varma", "Chowdary",
			"Yadav", "Kumar", "Prasad", "Setty", "Raju", "Murthy", "Babu", "Achary", "Patel", "Gupta", "Khan",
			"Shaik", "Thomas", "Joseph", "Mudiraj", "Nayak", "Pillai");

	static final List<String> OCCUPATIONS = List.of("Farmer", "Teacher", "Shop owner", "Software engineer",
			"Government employee", "Driver", "Business", "Bank employee", "Electrician", "Nurse", "Contractor",
			"Accountant", "Tailor", "Pharmacist", "Private employee");

	static final List<String> LOCALITIES = List.of("Gandhi Nagar", "Subhash Nagar", "Srinagar Colony",
			"Vidya Nagar", "Ashok Nagar", "Ramnagar", "Balaji Nagar", "Venkateswara Colony", "Sai Nagar",
			"Indira Nagar", "Teachers Colony", "Kakatiya Colony", "Lakshmi Nagar", "Prashanth Nagar");

	static <T> T pick(Random r, List<T> list) {
		return list.get(r.nextInt(list.size()));
	}

}
