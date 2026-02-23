{ Test 2: Multiple Classes and Object Composition }
program Test2;

type
  TPoint = class
  private
    FX: Integer;
    FY: Integer;
  public
    constructor Create(X: Integer; Y: Integer);
    function GetX: Integer;
    function GetY: Integer;
    procedure SetX(X: Integer);
    procedure SetY(Y: Integer);
    procedure Print;
  end;

  TRectangle = class
  private
    FTopLeft: TPoint;
    FWidth: Integer;
    FHeight: Integer;
  public
    constructor Create(X: Integer; Y: Integer; W: Integer; H: Integer);
    function GetArea: Integer;
    function GetPerimeter: Integer;
    procedure Print;
  end;

constructor TPoint.Create(X: Integer; Y: Integer);
begin
  FX := X;
  FY := Y;
end;

function TPoint.GetX: Integer;
begin
  Result := FX;
end;

function TPoint.GetY: Integer;
begin
  Result := FY;
end;

procedure TPoint.SetX(X: Integer);
begin
  FX := X;
end;

procedure TPoint.SetY(Y: Integer);
begin
  FY := Y;
end;

procedure TPoint.Print;
begin
  Write('(', FX, ', ', FY, ')');
end;

constructor TRectangle.Create(X: Integer; Y: Integer; W: Integer; H: Integer);
begin
  FTopLeft := TPoint.Create(X, Y);
  FWidth := W;
  FHeight := H;
end;

function TRectangle.GetArea: Integer;
begin
  Result := FWidth * FHeight;
end;

function TRectangle.GetPerimeter: Integer;
begin
  Result := 2 * (FWidth + FHeight);
end;

procedure TRectangle.Print;
begin
  Write('Rectangle at ');
  FTopLeft.Print;
  WriteLn(' Width=', FWidth, ' Height=', FHeight);
  WriteLn('  Area: ', GetArea);
  WriteLn('  Perimeter: ', GetPerimeter);
end;

var
  R1, R2: TRectangle;

begin
  WriteLn('=== Test 2: Object Composition ===');
  WriteLn('');

  R1 := TRectangle.Create(0, 0, 10, 5);
  R2 := TRectangle.Create(3, 7, 4, 8);

  WriteLn('Rectangle 1:');
  R1.Print;

  WriteLn('');
  WriteLn('Rectangle 2:');
  R2.Print;

  WriteLn('');
  WriteLn('Test 2 Complete!');
end.
