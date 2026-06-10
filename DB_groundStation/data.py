### 외부 DB에 접근하여 실시간으로 최근 데이터를 print하는 코드
import pymysql
import time

# 라즈베리파이 MySQL DB 연결 정보
pi_db_host = '172.20.10.3'       # 라즈베리파이의 IP 주소
pi_db_user = 'root'                 # 라즈베리파이 MySQL 사용자 이름
pi_db_password = 'Gocks1206!'
pi_db_name = 'test'          # 라즈베리파이 데이터베이스 이름
pi_table_name = 'drive2_table'      # 라즈베리파이 테이블 이름

# 라즈베리파이 DB에 연결
def connect_to_pi_db():
    try:
        conn = pymysql.connect(
            host=pi_db_host,
            user=pi_db_user,
            password=pi_db_password,
            database=pi_db_name,
            autocommit=True  # 자동 커밋 설정
        )
        print("Connected to Raspberry Pi MySQL database successfully.")
        return conn
    except pymysql.MySQLError as e:
        print(f"Failed to connect to Raspberry Pi MySQL: {e}")
        return None

# 새로운 데이터를 확인하여 출력하기
def check_and_print_new_data(pi_conn, last_id):
    try:
        with pi_conn.cursor() as pi_cursor:
            # 마지막 id 이후의 가장 최근 데이터를 가져오는 쿼리
            query = f"SELECT id, RSRP, RSRQ, RSSNR, download, latitude, longitude, earfcn FROM {pi_table_name} WHERE id > %s ORDER BY id ASC LIMIT 1"
            pi_cursor.execute(query, (last_id,))
            row = pi_cursor.fetchone()

            if row:
                print(f"New Data: {row[1:]}")  # 새로운 데이터 출력
                return row[0]  # 가장 최근 데이터의 id 반환
            else:
                return last_id

    except pymysql.MySQLError as e:
        print(f"Failed to retrieve data: {e}")
        return last_id

# 메인 작업 흐름
pi_conn = connect_to_pi_db()

if pi_conn is None:
    print("Failed to connect to the Raspberry Pi database. Exiting.")
    exit()

last_id = 0  # 초기 id 값 설정

try:
    while True:
        last_id = check_and_print_new_data(pi_conn, last_id)
        time.sleep(0.2)  # 0.2초마다 데이터 확인

except KeyboardInterrupt:
    print("프로그램을 종료합니다.")
finally:
    pi_conn.close()

