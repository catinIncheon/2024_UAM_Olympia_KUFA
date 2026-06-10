import serial
import time
import pymysql

# 시리얼 포트 설정
port = '/dev/ttyUSB0'  # 시리얼 포트 이름 (USB 시리얼 디바이스)
baud_rate = 115200  # 보레이트 설정
timeout = 0.25  # 타임아웃 설정 (초)

# MySQL DB Connect
conn = pymysql.connect(
    host='localhost',       # MySQL 서버 호스트 이름
    user='root',            # MySQL 사용자 이름
    password='Gocks1206!',  # MySQL 비밀번호
    database='test'         # 데이터베이스 이름
)
cursor = conn.cursor()

def open_serial_port():
    """시리얼 포트를 엽니다."""
    try:
        ser = serial.Serial(port, baud_rate, timeout=timeout, rtscts=False)
        print("Serial port opened successfully.")
        return ser
    except serial.SerialException as e:
        print(f"Failed to open serial port: {e}")
        return None

def close_serial_port(ser):
    """시리얼 포트를 닫습니다."""
    if ser and ser.is_open:
        try:
            ser.close()
            print("Serial port closed.")
        except serial.SerialException as e:
            print(f"Failed to close serial port: {e}")

def create_table_if_not_exists():
    """테이블이 존재하지 않으면 생성합니다."""
    create_table_query = """
    CREATE TABLE IF NOT EXISTS drive2_table (
        id INT AUTO_INCREMENT PRIMARY KEY,
        RSRP INT,
        RSRQ INT,
        RSSNR INT,
        download FLOAT,
        latitude DOUBLE,
        longitude DOUBLE,
        earfcn INT
    )
    """
    cursor.execute(create_table_query)
    conn.commit()

def insert_data(data_list):
    """데이터를 데이터베이스에 삽입합니다."""
    if len(data_list) == 7:  # 데이터 개수가 정확한지 확인, 버퍼로 인해 값이 밀리거나 하면 DB에 업로드하지 않음.
        try:
            insert_query = """
            INSERT INTO drive2_table (RSRP, RSRQ, RSSNR, Download, Latitude, Longitude, Earfcn)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            
            data_list = [int(data_list[0]), int(data_list[1]), int(data_list[2]), data_list[3], data_list[4], data_list[5], int(data_list[6])]
            cursor.execute(insert_query, data_list)
            conn.commit()
            print("Data inserted successfully.")
        except pymysql.MySQLError as e:
            print(f"Failed to insert data: {e}")
    else:
        print("Data length mismatch. Data not inserted.")

ser = open_serial_port()

if ser is None:
    print("Could not open serial port. Exiting.")
    exit()

create_table_if_not_exists()

try:
    while True:
        try:
            if ser.in_waiting > 0:
                data = ser.read_until().decode('utf-8').rstrip()
                if data:
                    print(f"Received: {data}")
                    data_list = data.split()
                    # 데이터 리스트를 각각의 타입으로 변환
                    insert_data(data_list)
        except serial.SerialException as e:
            print(f"SerialException occurred: {e}")
        except pymysql.MySQLError as e:
            print(f"MySQL error occurred: {e}")

        time.sleep(0.1)  # CPU 점유율을 줄이기 위해 잠시 대기

except KeyboardInterrupt:
    print("프로그램을 종료합니다.")
finally:
    close_serial_port(ser)
    cursor.close()
    conn.close()
