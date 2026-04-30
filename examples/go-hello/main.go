// This example builds a simple static linux/riscv64 Go program for the
// GraalRISCV user-mode runtime.
package main

import "fmt"

func main() {
	s := "gopher"
	fmt.Printf("Hello and welcome, %s!\n", s)

	for i := 1; i <= 5; i++ {
		fmt.Println("i =", 100/i)
	}
}
