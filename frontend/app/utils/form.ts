export const text = (data: FormData, name: string) =>
  String(data.get(name) || "").trim();
export const number = (data: FormData, name: string) => Number(data.get(name));
export function userInput(data: FormData) {
  return {
    run: text(data, "run"),
    fullName: text(data, "fullName"),
    email: text(data, "email"),
  };
}
