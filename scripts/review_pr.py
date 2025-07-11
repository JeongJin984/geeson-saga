import os
import json
import requests

# 환경 변수
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
GITHUB_TOKEN = os.getenv("GEESON_REPO_TOKEN")
REPO = os.getenv("GITHUB_REPOSITORY")  # e.g., "owner/repo"
API_BASE = f"https://api.github.com/repos/{REPO}"

HEADERS = {
    "Authorization": f"token {GITHUB_TOKEN}",
    "Accept": "application/vnd.github.v3+json"
}

def get_pr_number():
    event_path = os.getenv("GITHUB_EVENT_PATH")
    if not event_path:
        raise EnvironmentError("GITHUB_EVENT_PATH is not set")
    with open(event_path, "r") as f:
        event = json.load(f)
    return event["pull_request"]["number"]

def get_diff(pr_number):
    # PR 정보 요청
    pr_url = f"{API_BASE}/pulls/{pr_number}"
    pr_resp = requests.get(pr_url, headers=HEADERS)
    if pr_resp.status_code != 200:
        raise Exception(f"Failed to get PR info: {pr_resp.status_code} {pr_resp.text}")
    pr_data = pr_resp.json()

    # diff_url에서 실제 diff 데이터 요청
    diff_url = pr_data.get("diff_url")
    if not diff_url:
        raise Exception("diff_url not found in PR data")
    diff_resp = requests.get(diff_url, headers=HEADERS)
    if diff_resp.status_code != 200:
        raise Exception(f"Failed to fetch diff: {diff_resp.status_code} {diff_resp.text}")
    return diff_resp.text

def review_with_chatgpt(diff):
    print("Sending diff to OpenAI API...")
    url = "https://api.openai.com/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {OPENAI_API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "gpt-4o",  # 또는 gpt-4o
        "messages": [
            {
                "role": "system",
                "content": (
                    "너는 매우 경험 많은 소프트웨어 아키텍트이자 코드 리뷰어다. "
                    "코드 품질, 설계, 네이밍, 중복, 책임 분리, 확장성, 예외 처리 등 모든 측면을 기준으로 "
                    "비판적으로 리뷰하라. **작은 문제라도 절대 넘어가지 마라.** "
                    "칭찬은 생략하고, 반드시 개선이 필요한 점만 구체적으로 지적하라. "
                    "리뷰는 한국어로 작성하고, 각 항목은 번호로 나열하라. 설명은 간결하지만 명확하게 작성해라."
                )
            },
            {
                "role": "user",
                "content": f"다음은 Pull Request의 Git diff 내용이다. 코드의 문제점을 최대한 비판적으로 리뷰해줘:\n\n{diff}"
            }
        ],
        "temperature": 0.3
    }

    response = requests.post(url, headers=headers, json=payload)
    if response.status_code != 200:
        raise Exception(f"OpenAI API failed: {response.status_code} {response.text}")

    result = response.json()
    return result["choices"][0]["message"]["content"]

def post_comment(pr_number, comment_body):
    comment_url = f"{API_BASE}/issues/{pr_number}/comments"
    data = {"body": f"🤖 **ChatGPT Code Review**\n\n{comment_body}"}
    resp = requests.post(comment_url, headers=HEADERS, json=data)
    if resp.status_code != 201:
        raise Exception(f"Failed to post comment: {resp.status_code} {resp.text}")
    print("✅ Review comment posted successfully.")

if __name__ == "__main__":
    pr_number = get_pr_number()
    diff = get_diff(pr_number)
    review = review_with_chatgpt(diff)
    post_comment(pr_number, review)