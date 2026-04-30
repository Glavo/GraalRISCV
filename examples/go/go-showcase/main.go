// This example builds a static linux/riscv64 Go workload that exercises
// standard-library parsing, sorting, compression, hashing, and goroutines.
package main

import (
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"sort"
	"sync"
)

type record struct {
	Name  string `json:"name"`
	Score int    `json:"score"`
}

func main() {
	const input = `[
		{"name":"alpha","score":17},
		{"name":"bravo","score":23},
		{"name":"charlie","score":31},
		{"name":"delta","score":42},
		{"name":"echo","score":29}
	]`

	var records []record
	if err := json.Unmarshal([]byte(input), &records); err != nil {
		panic(err)
	}

	sort.Slice(records, func(left, right int) bool {
		return records[left].Score > records[right].Score
	})

	total := 0
	var digestInput bytes.Buffer
	for _, item := range records {
		total += item.Score
		fmt.Fprintf(&digestInput, "%s:%d;", item.Name, item.Score)
	}

	digest := sha256.Sum256(digestInput.Bytes())
	compressedSize := gzipSize(digestInput.Bytes())
	workerTotal := workerSum(records)

	fmt.Printf("records=%d\n", len(records))
	fmt.Printf("top=%s:%d\n", records[0].Name, records[0].Score)
	fmt.Printf("total=%d\n", total)
	fmt.Printf("worker-total=%d\n", workerTotal)
	fmt.Printf("gzip-bytes=%d\n", compressedSize)
	fmt.Printf("sha256=%s\n", hex.EncodeToString(digest[:]))
	fmt.Println("go-showcase-ok")
}

func gzipSize(data []byte) int {
	var output bytes.Buffer
	writer := gzip.NewWriter(&output)
	if _, err := writer.Write(data); err != nil {
		panic(err)
	}
	if err := writer.Close(); err != nil {
		panic(err)
	}
	return output.Len()
}

func workerSum(records []record) int {
	results := make(chan int, len(records))
	var group sync.WaitGroup
	for _, item := range records {
		item := item
		group.Add(1)
		go func() {
			defer group.Done()
			results <- item.Score * item.Score
		}()
	}
	group.Wait()
	close(results)

	total := 0
	for value := range results {
		total += value
	}
	return total
}
