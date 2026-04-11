from fastapi import FastAPI, Depends
from pydantic import BaseModel
import re, requests, urllib.parse
from transformers import pipeline
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from datetime import datetime
from fastapi.middleware.cors import CORSMiddleware
import os
import oracledb


# 1. 지갑(Wallet) 경로 설정
os.environ["TNS_ADMIN"] = "C:/oraclepw"
# 2. 띡 모드 활성화 (복사한 경로 그대로 붙여넣기)
try:
    # 경로 앞에 r을 붙여야 역슬래시(\) 인식이 잘 됩니다.
    client_path = r"C:\instantclient-basiclite-windows.x64-23.26.1.0.0\instantclient_23_0"
    oracledb.init_oracle_client(lib_dir=client_path)
    print("✓ 오라클 띡 모드(Thick Mode) 가동 성공!")
except Exception as e:
    print(f"✓ 띡 모드 가동 실패: {e}")


app = FastAPI(title="SpendingData Category Classification API")

# ------------------------------
# CORS 설정 (브라우저 통신 허용)
# ------------------------------
# 이 부분 있어야 브라우저의 OPTIONS 요청을 통과시킴.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],     # 모든 도메인 허용
    allow_credentials=True,
    allow_methods=["*"],   # GET, POST, OPTIONS 등 모든 메서드 허용
    allow_headers=["*"],   # 모든 헤더 허용
)



# ------------------------------
# 모델 & 카테고리 초기화
# ------------------------------
classifier = pipeline("zero-shot-classification", model="facebook/bart-large-mnli")

my_categories = [
    "교육", "교통", "미용", "생활", "쇼핑", "식비",
    "의료", "여가,취미,예술", "카페,간식,디저트", "편의점, 마트", "카테고리 없음"
]

shop_rules = {
    '쿠팡': '쇼핑',
    '배달의민족': '식비',
    '우아한형제들': '식비',
    '배민': '식비',
    '카카오': '쇼핑',
    '티머니': '교통, 자동차',
    'CGV' : '여가,취미,예술',
    '커피' : '카페,간식,디저트',
    'CU' : '편의점, 마트, 잡화',
    '씨유' : '편의점, 마트, 잡화',
    'GS25' : '편의점, 마트, 잡화',
    '세븐일레븐' : '편의점, 마트, 잡화',
    '이마트24' : '편의점, 마트, 잡화',
    '이마트' : '편의점, 마트, 잡화',
    '교통' : '교통, 자동차'
}

raw_cat_rules = {
    '육류,고기요리': '식비',
    '한식': '식비',
    '음식점' : '식비',
    '전문,기술서비스': '생활',
    '패션' : '쇼핑',
    '문화,예술' : '여가,취미,예술',
    '안경원' : '생활',
    '오락' : '여가,취미,예술'
}

NAVER_CLIENT_ID = "UF5u6PjtWKqTzGhw4aL5"
NAVER_CLIENT_SECRET = "UV2YTZbqdh"


# ------------------------------
# Pydantic 모델 정의
# ------------------------------
class Transaction(BaseModel):  # 웹에서 받을 JSON 데이터 형식 정의
    vendor: str
    transaction_date: str   # "YY/MM/DD"
    amount: float
    user_id: int   # 👈 추가


# ------------------------------
# Oracle DB 연결
# ------------------------------

# 자바의 TNS_ADMIN=... 과 동일한 효과를 냅니다.
# os.environ["TNS_ADMIN"] = "C:/oraclepw"

# 환경 변수를 설정해서 URL 뒤의 물음표(?) 부분 지움
DB_URL = "oracle+oracledb://ADMIN:Heeyoun1220!@stoxle_low?events=true"

engine = create_engine(DB_URL, echo=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


# ------------------------------
# Helper 함수
# ------------------------------
# 네이버 api로 구매처를 통해 네이버정의 카테고리 받아옴
def get_naver_category(query: str):  # query: 구매처
    clean_query = re.sub(r'(\s점$|점$|\(주\)|주식회사)', '', query)
    encText = urllib.parse.quote(clean_query)
    url = f"https://openapi.naver.com/v1/search/local.json?query={encText}&display=1"
    headers = {"X-Naver-Client-Id": NAVER_CLIENT_ID, "X-Naver-Client-Secret": NAVER_CLIENT_SECRET}
    try:
        response = requests.get(url, headers=headers)
        if response.status_code == 200:
            items = response.json().get('items')
            if items:
                return items[0]['category']
        return "카테고리 없음"
    except:
        return "카테고리 없음"

# 카테고리 단순화 - 네이버 정의 카테고리의 대분류만 추출
def simplify_category(naver_category: str):
    return naver_category.split('>')[0]

# 머신러닝 기반 분류 - zero-shot모델이 가장 적합한 my_categories라벨 선택(카테고리 종류 축소)
def ml_classify(naver_cat: str):
    if naver_cat == "카테고리 없음":
        return "카테고리 없음"
    result = classifier(naver_cat, my_categories)
    return result['labels'][0]


# 카테고리 수동 보정
def finalize_category(vendor: str, naver_raw: str, ml_cat: str):
    for key, val in shop_rules.items():
        if key in vendor:
            return val
    for key, val in raw_cat_rules.items():
        if key in naver_raw:
            return val
    return ml_cat

# 분류 최종 실행
def classify_transaction(transaction: dict):
    vendor = transaction['vendor']
    naver_raw = get_naver_category(vendor)
    simple_cat = simplify_category(naver_raw)
    ml_cat = ml_classify(simple_cat)
    final_cat = finalize_category(vendor, naver_raw, ml_cat)
    transaction['naver_raw'] = naver_raw
    transaction['category'] = final_cat

    # month 자동 계산
    tx_date = datetime.strptime(transaction['transaction_date'], "%y/%m/%d")
    transaction['month'] = tx_date.month
    transaction['year'] = tx_date.year  # 👈 이거 추가

    return transaction




# ------------------------------
# DB 저장 함수
# ------------------------------

def save_transaction_to_db(tx: dict):
    session = SessionLocal()
    try:
        tx_date = datetime.strptime(tx['transaction_date'], "%y/%m/%d").date()
        session.execute(
            text("""
                INSERT INTO SPENDING_DATA
                    (SPEND_ID, USER_ID, MONTH, YEAR, TRANSACTION_DATE, AMOUNT, VENDOR, CATEGORY)
                VALUES (SEQ_SPENDING_ID.NEXTVAL, :user_id, :month, :year, :transaction_date, :amount, :vendor, :category)
            """),
            {
                "user_id": tx['user_id'],
                "month": tx['month'],
                "year": tx['year'],
                "transaction_date": tx_date,
                "amount": tx['amount'],
                "vendor": tx['vendor'],
                "category": tx.get('category')
            }
        )
        session.commit()
        return True
    except Exception as e:
        session.rollback()
        print("DB 저장 오류:", e)
        return False
    finally:
        session.close()


# ------------------------------
# API Endpoint
# ------------------------------
@app.post("/classify_transaction")
def classify(transaction: Transaction):
    tx_dict = transaction.dict()  # user_id는 transaction 안에 들어있음

    # 분류
    result = classify_transaction(tx_dict)
    print(f"--- 분류 결과 ---")
    print(f"구매처: {result['vendor']}")
    print(f"카테고리: {result['category']}")
    print(f"금액: {result['amount']}")

    # DB 저장
    success = save_transaction_to_db(result)
    result['saved'] = success

    return {"result": result}
# /classify_transaction 경로로 POST 요청 받음
# 요청 JSON → Transaction 모델 → Python 딕셔너리 → 분류 함수 호출
# 최종 결과 JSON으로 반환
# 디비 저장

# 코드 맨 마지막 줄에 추가
if __name__ == "__main__":
    import uvicorn
    # port는 9000번으로 설정 (자바 설정과 맞춤)
    uvicorn.run(app, host="127.0.0.1", port=8000)


