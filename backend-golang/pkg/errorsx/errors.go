package errorsx

// AppError is a reusable error envelope for API responses.
type AppError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
