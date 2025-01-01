package info.kgeorgiy.ja.elagina.student;

import info.kgeorgiy.java.advanced.student.*;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StudentDB implements StudentQuery {

    private <T> Stream<T> mappedStudents(List<Student> students, Function<Student, T> func) {
        return students.stream()
                .map(func);
    }

    private String fullName(Student student) {
        return student.getFirstName() + " " + student.getLastName();
    }

    private <T> List<T> mappedStudentsToList(List<Student> students, Function<Student, T> param) {
        return mappedStudents(students, param).toList();
    }
    @Override
    public List<String> getFirstNames(List<Student> students) {
        return mappedStudentsToList(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return mappedStudentsToList(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return mappedStudentsToList(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return mappedStudentsToList(students, this::fullName);
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return mappedStudents(students, Student::getFirstName).collect(Collectors.toSet());
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return students.stream()
                .max(Student::compareTo)
                .map(Student::getFirstName)
                .orElse("");
    }

    private Stream<Student> sortedStudents(Collection<Student> students, Comparator<Student> cmp) {
        return students.stream()
                .sorted(cmp);
    }

    private List<Student> sortedStudentsToList(Collection<Student> students, Comparator<Student> param) {
        return sortedStudents(students, param).toList();
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortedStudentsToList(students, Student::compareTo);
    }

    private final Comparator<Student> studentComparator = Comparator.comparing(Student::getLastName)
            .thenComparing(Student::getFirstName).reversed()
            .thenComparing(Student::getId);

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortedStudentsToList(students, studentComparator.reversed());
    }

    private <T> Stream<Student> filteredStudents(Collection<Student> students, Function<Student, T> func, T value) {
        return sortedStudents(students.stream()
                .filter(student -> func.apply(student).equals(value))
                .toList(), studentComparator.reversed());
    }


    private <T> List<Student> filteredStudentsToList(Collection<Student> students,  Function<Student, T> param, T name) {
        return filteredStudents(students, param, name).toList();
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return filteredStudentsToList(students, Student::getFirstName, name);
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return filteredStudentsToList(students, Student::getLastName, name);
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return filteredStudentsToList(students, Student::getGroup, group);
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return filteredStudents(students, Student::getGroup, group)
                .collect(Collectors.toMap(Student::getLastName,
                        Student::getFirstName,
                        BinaryOperator.minBy(Comparator.naturalOrder())));
    }
}
