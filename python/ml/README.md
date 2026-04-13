# 런타임 ML 파일 안내

이 폴더는 웹 프로젝트에서 **실행 시 필요한 파일만** 보관합니다.

## 서버 실행에 필요한 파일

- `predict.py`
- `models/lgbm_model.pkl`
- `article_predict.py`
- `models/article_lgbm_model.pkl`
- `data/news_data.db`
- `data/oracle_company.csv`
- `data/oracle_sector.csv`

## 학습 코드가 없는 이유

이 저장소는 **웹 서비스 런타임용 프로젝트**입니다.  
모델 학습은 별도 작업 폴더나 별도 프로젝트에서 진행하고,
학습이 끝난 뒤 **실행에 필요한 결과물만** 이 폴더로 가져오는 구조입니다.

따라서 아래 항목은 서버 실행에 필요하지 않습니다.

- feature builder 스크립트
- train 스크립트
- 샘플 데이터셋
- 학습 검증용 메타 파일

## 모델을 다시 학습했을 때 교체할 파일

### 종목 단위 익일 방향 예측 모델

- `models/lgbm_model.pkl`

### 기사 단위 주가 영향도 모델

- `models/article_lgbm_model.pkl`

### 뉴스 입력 데이터

- `data/news_data.db`
- `data/oracle_company.csv`
- `data/oracle_sector.csv`

## 관련 Spring 설정

설정 위치: `src/main/resources/application.properties`

- `ai.predict.script`
- `ai.predict.model`
- `ai.article-impact.script`
- `ai.article-impact.model`
- `ai.predict.sqlite`
- `ai.predict.oracle-company`
- `ai.predict.oracle-sector`

팀원마다 Python 가상환경 경로가 다를 수 있으므로,
필요하면 각자 로컬 환경변수 `PYTHON_PATH` 를 지정해서 사용하면 됩니다.
